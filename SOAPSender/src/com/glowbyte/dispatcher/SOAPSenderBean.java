package com.glowbyte.dispatcher;

import javax.ejb.ActivationConfigProperty;
import javax.ejb.MessageDriven;
import javax.jms.*;
import javax.naming.InitialContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;

/*
* TODO: Add support for app servers other than GlassFish.
* TODO: Find a way to move static MDB pool configuration from sun-ejb-jar.xml to config/GUI.
* */

@MessageDriven(
        activationConfig = {
                @ActivationConfigProperty(propertyName = "destinationType", propertyValue = "javax.jms.Queue"),
                @ActivationConfigProperty(propertyName = "useJndi", propertyValue = "true"),
                @ActivationConfigProperty(propertyName="destination", propertyValue="jms/soapdispatcher/inqueue")
        },
        name = "SOAPSenderEJB")

public class SOAPSenderBean implements javax.jms.MessageListener {
    public SOAPSenderBean() {
    }

    public void onMessage (Message m) {
        try {
            System.out.println("Got message! Text: " + ((TextMessage) m).getText());

            if (m instanceof TextMessage) {
                URLConnection urlConnection = new URL("http://localhost:8080").openConnection();
                urlConnection.setUseCaches(false);
                urlConnection.setDoOutput(true);
                urlConnection.setRequestProperty("accept-charset", "UTF-8");
                urlConnection.setRequestProperty("content-type", "text/xml");

                OutputStreamWriter writer = null;
                try {
                    writer = new OutputStreamWriter(urlConnection.getOutputStream(), "UTF-8");
                    writer.write(((TextMessage) m).getText());
                } finally {
                    if (writer != null) try { writer.close(); } catch (IOException logOrIgnore) {}
                }

                InputStream result = urlConnection.getInputStream();

                InitialContext ic = new InitialContext();

                QueueConnectionFactory connectionFactory = (QueueConnectionFactory)ic.lookup("jms/SOAPDispatcherConnFactory");
                Queue queue = (Queue) ic.lookup("jms/soapdispatcher/outqueue");

                QueueConnection conn = connectionFactory.createQueueConnection();
                QueueSession sess = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
                QueueSender sender = sess.createSender(queue);


                String output = new Scanner(result, "UTF-8").useDelimiter("\\A").next();

                TextMessage jmsMsg = sess.createTextMessage(output);

                conn.start();

                sender.send(jmsMsg);

                sender.close();
                sess.close();
                conn.close();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}
