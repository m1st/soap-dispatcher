package com.glowbyte.dispatcher;

import javax.annotation.Resource;
import javax.jms.*;
import javax.naming.InitialContext;
import javax.servlet.ServletContext;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.*;
import javax.xml.ws.handler.MessageContext;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.Scanner;

/*
* TODO: Synchronous mode.
* */

@WebServiceProvider
@ServiceMode(value = Service.Mode.MESSAGE)
public class SOAPReceiverBean implements Provider<Source> {

    @Resource
    private WebServiceContext context;

    public Source invoke(Source msg) {

        InitialContext ic = null;
        try {
            ic = new InitialContext();

            ServletContext servletContext =
                    (ServletContext) context.getMessageContext().get(MessageContext.SERVLET_CONTEXT);

            QueueConnectionFactory connectionFactory = (QueueConnectionFactory)ic.lookup("jms/SOAPDispatcherConnFactory");
            Queue queue = (Queue) ic.lookup("jms/soapdispatcher/inqueue");

            QueueConnection conn = connectionFactory.createQueueConnection();
            QueueSession sess = conn.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
            QueueSender sender = sess.createSender(queue);

            StreamSource xsl = new StreamSource(servletContext.getResourceAsStream("/config/transform.xsl"));

            Transformer transformer = TransformerFactory.newInstance().newTransformer(xsl);
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            StreamResult sr = new StreamResult(writer);
            transformer.transform(msg, sr);
            String output = writer.getBuffer().toString();

            TextMessage jmsMsg = sess.createTextMessage(output);

            conn.start();

            sender.send(jmsMsg);

            sender.close();
            sess.close();
            conn.close();


            String body = new Scanner(servletContext.getResourceAsStream("/config/success.xml")).useDelimiter("\\A").next();
            Source source = new StreamSource(new ByteArrayInputStream(body.getBytes()));
            return source;
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
