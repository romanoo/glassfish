package org.glassfish.test.jms.activationproperties.client;

import javax.naming.*;
import javax.jms.*;
import org.glassfish.test.jms.activationproperties.ejb.*;
import com.sun.ejte.ccl.reporter.SimpleReporterAdapter;

public class Client {
    private final static SimpleReporterAdapter STAT = new SimpleReporterAdapter("appserv-tests");

    public static void main (String[] args) {
        STAT.addDescription("subscriptionScope-cluster-instanceScopeNonDurableWithNameID");
        Client client = new Client(args);
        client.doTest();
        STAT.printSummary("subscriptionScope-cluster-instanceScopeNonDurableWithNameID");
    }

    public Client (String[] args) {
    }

    public void doTest() {
        String ejbName = "MySessionBean";
        String text = "Hello World!";
        try {
            Context ctx = new InitialContext();
            MySessionBeanRemote beanRemote = (MySessionBeanRemote) ctx.lookup(MySessionBeanRemote.RemoteJNDIName);
            beanRemote.sendMessage(text);

            try {
                Thread.sleep(10000);
            } catch (Exception e) {}

            String result = beanRemote.checkMessage(text);
            if (result.startsWith("Success"))
                STAT.addStatus("subscriptionScope-cluster-instanceScopeNonDurableWithNameID " + ejbName, STAT.PASS);
            else {
                System.out.println(result);
                STAT.addStatus("subscriptionScope-cluster-instanceScopeNonDurableWithNameID " + ejbName, STAT.FAIL);
            }
        } catch(Exception e) {
            e.printStackTrace();
            STAT.addStatus("subscriptionScope-cluster-instanceScopeNonDurableWithNameID " + ejbName, STAT.FAIL);
        }
    }
}