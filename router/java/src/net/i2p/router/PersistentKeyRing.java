package net.i2p.router;

import java.io.IOException;
import java.io.Writer;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import net.i2p.data.Base64;
import net.i2p.data.DataFormatException;
import net.i2p.data.Destination;
import net.i2p.data.Hash;
import net.i2p.data.LeaseSet;
import net.i2p.data.SessionKey;
import net.i2p.router.TunnelPoolSettings;
import net.i2p.util.KeyRing;

/**
 *  ConcurrentHashMap with backing in the router.config file.
 *  router.keyring.key.{base64 hash, with = replaced with $}={base64 session key}
 *  Caution - not all HashMap methods are overridden.
 */
public class PersistentKeyRing extends KeyRing {
    private RouterContext _ctx;
    private static final String PROP_PFX = "router.keyring.key.";

    public PersistentKeyRing(RouterContext ctx) {
        super();
        _ctx = ctx;
        addFromProperties();
    }

    public SessionKey put(Hash h, SessionKey sk) {
        SessionKey old = super.put(h, sk);
        if (!sk.equals(old)) {
            _ctx.router().setConfigSetting(PROP_PFX + h.toBase64().replace("=", "$"),
                                           sk.toBase64());
            _ctx.router().saveConfig();
        }
        return old;
    }

    public SessionKey remove(Hash h) {
        _ctx.router().removeConfigSetting(PROP_PFX + h.toBase64().replace("=", "$"));
        _ctx.router().saveConfig();
        return super.remove(h);
    }

    private void addFromProperties() {
        for (Iterator iter = _ctx.getPropertyNames().iterator(); iter.hasNext(); ) {
            String prop = (String) iter.next();
            if (!prop.startsWith(PROP_PFX))
                continue;
            String key = _ctx.getProperty(prop);
            if (key == null || key.length() != 44)
                continue;
            String hb = prop.substring(PROP_PFX.length());
            hb.replace("$", "=");
            Hash dest = new Hash();
            SessionKey sk = new SessionKey();
            try {
                dest.fromBase64(hb);
                sk.fromBase64(key);
                super.put(dest, sk);
            } catch (DataFormatException dfe) { continue; }
        }
    }

    public void renderStatusHTML(Writer out) throws IOException {
        StringBuffer buf = new StringBuffer(1024);
        buf.append("\n<table border=\"1\"><tr><th align=\"left\">Destination Hash<th align=\"left\">Name or Dest.<th align=\"left\">Session Key</tr>");
        for (Entry<Hash, SessionKey> e : entrySet()) {
            buf.append("\n<tr><td>");
            Hash h = e.getKey();
            buf.append(h.toBase64().substring(0, 6)).append("...");
            buf.append("<td>");
            LeaseSet ls = _ctx.netDb().lookupLeaseSetLocally(h);
            if (ls != null) {
                Destination dest = ls.getDestination();
                if (_ctx.clientManager().isLocal(dest)) {
                    TunnelPoolSettings in = _ctx.tunnelManager().getInboundSettings(h);
                    if (in != null && in.getDestinationNickname() != null)
                        buf.append(in.getDestinationNickname());
                    else
                        buf.append(dest.toBase64().substring(0, 6)).append("...");
                } else {
                    String host = _ctx.namingService().reverseLookup(dest);
                    if (host != null)
                        buf.append(host);
                    else
                        buf.append(dest.toBase64().substring(0, 6)).append("...");
                }
            }
            buf.append("<td>");
            SessionKey sk = e.getValue();
            buf.append(sk.toBase64());
        }
        buf.append("\n</table>\n");
        out.write(buf.toString());
        out.flush();
    }
}
