/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor.metric;

import bisq.monitor.Metric;
import bisq.monitor.OnionParser;
import bisq.monitor.Reporter;

import bisq.network.p2p.NodeAddress;

import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Fetches fee data from the configured price nodes.
 * Based on the work of HarryMcFinned.
 * 
 * @author Florian Reimair
 * @author HarryMcFinned
 *
 */
@Slf4j
public class PriceNodeStats extends Metric {

    private static final String HOSTS = "run.hosts";

    public PriceNodeStats(Reporter reporter) {
        super(reporter);
    }

    @Override
    protected void execute() {
        // poor mans JSON parser
        Pattern p = Pattern.compile("\"(.+)\" ?: ?(\\d+)");

        try {
            Map<String, String> result = new HashMap<>();

            // fetch proxy
            Tor tor = Tor.getDefault();
            checkNotNull(tor, "tor must not be null");
            Socks5Proxy proxy = tor.getProxy();

            // for each configured host
            for (String current : configuration.getProperty(HOSTS, "").split(",")) {
                // parse Url
                NodeAddress tmp = OnionParser.getNodeAddress(current);

                // connect
                SocksSocket socket = new SocksSocket(proxy, tmp.getHostName(), tmp.getPort());

                // prepare to receive data
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // ask for data
                PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
                out.println("GET /getFees/");
                out.println();
                out.flush();

                //sift through the received lines and see if we got something json-like
                String line;
                while((line = in.readLine()) != null) {
                    Matcher matcher = p.matcher(line);
                    if(matcher.find())
                        result.put(OnionParser.prettyPrint(tmp) + "." + matcher.group(1), matcher.group(2));
                }

                // close all the things
                in.close();
                out.close();
                socket.close();

                // report
                reporter.report(result, "bisq." + getName());
            }
        } catch (TorCtlException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
