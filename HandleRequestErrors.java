package com.bmw.il.it.gcap.mapping;

import com.bmw.il.it.gcap.utils.Utils;
import com.ibm.broker.javacompute.MbJavaComputeNode;
import com.ibm.broker.plugin.MbElement;
import com.ibm.broker.plugin.MbException;
import com.ibm.broker.plugin.MbMessage;
import com.ibm.broker.plugin.MbMessageAssembly;
import com.ibm.broker.plugin.MbOutputTerminal;
import com.ibm.broker.plugin.MbService;
import com.ibm.broker.plugin.MbUserException;
import org.w3c.dom.Document;
import com.ibm.broker.plugin.MbXMLNSC;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import com.bmw.il.utils.MbExceptionUtils;

public class HandleRequestErrors extends MbJavaComputeNode {

    protected JAXBContext jaxbContext = null;

    @Override
    public void onInitialize() throws MbException {
        try {

            jaxbContext = JAXBContext.newInstance("com.bmw.il.dfe.creditrating_01_01_messagemodel");
        } catch (final JAXBException e) {
            // This exception will cause the deploy of this Java compute node to
            // fail
            // Typical cause is the JAXB package above is not available
            throw new MbUserException(this, "onInitialize()", "", "", e.toString(),
                    MbExceptionUtils.buildInserts(e));
        }
    }

    @Override
    public void evaluate(MbMessageAssembly inAssembly) throws MbException {
        final MbOutputTerminal out = getOutputTerminal("out");

        // obtain the input message data
        final MbMessage inMessage = inAssembly.getMessage();

        // create a new empty output message
        final MbMessage outMessage = new MbMessage();
        final MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly, outMessage);

        Utils.copyMessageHeaders(inMessage, outMessage);
        String strFullExceptionTree = "strFullExceptionTree not initialized";

        try {
            strFullExceptionTree = Utils.getXMLContentFromDocument(inAssembly.getExceptionList()
                    .getDOMDocument());
            // refer to the Input ExceptionList
            MbElement exceptionList = inAssembly.getExceptionList().getRootElement();
            // traverse through the ExceptionList till you reach the innermost
            // Exception
            while (exceptionList != null && exceptionList.getLastChild() != null
                    && exceptionList.getLastChild().getName() != null
                    && exceptionList.getLastChild().getName().endsWith("Exception")) {
                exceptionList = exceptionList.getLastChild();
            }

            String errorText = "IIB exceptionList stackTrace not initialized";
            // Trying to find the child by name Text under the innermost
            // Exception
            final MbElement textElement = exceptionList.getFirstElementByPath("Text");
            if (textElement != null && textElement.getValueAsString() != null) {
                errorText = textElement.getValueAsString();
            }
            errorText = errorText.concat(" || ");

            // Point to the last child of the innermost Exception which is
            // usually Insert.
            MbElement insert = exceptionList.getLastChild();
            // Try to concatenate the value of the Text element under each
            // Insert element
            while (insert != null && "Insert".equals(insert.getName())) {
                if (insert.getLastChild() != null && insert.getLastChild().getValueAsString() != null) {
                    errorText = errorText.concat(insert.getLastChild().getValueAsString());
                    errorText = errorText.concat(" || ");
                }
                insert = insert.getPreviousSibling();
            }

            // Add user code below to build the new output data by updating
            // your Java objects or building new Java objects
            final ErrorMapper errorMapper = new ErrorMapper();
            final Object outMsgJavaObj = errorMapper.mapToSoapFault(strFullExceptionTree);

            final Object proposalId = inMessage.evaluateXPath("string(//proposalId)");
            final Object xRefCode = inMessage.evaluateXPath("string(//xrefCode)");
            final Object[] inserts = new Object[] { proposalId, xRefCode };
            MbService.logError(//
                    this,// object
                    "evaluate",// method name
                    "messages", // resource bundle
                    "error", // message key
                    errorText, // text
                    inserts);

            // set the required Broker domain to for the output message, eg
            // XMLNSC
            final Document outDocument = outMessage.createDOMDocument(MbXMLNSC.PARSER_NAME);
            // marshal the new or updated output Java object class into the
            // Broker tree

            // outMsgJavaObj = getInvalidRequestMessage();

            jaxbContext.createMarshaller().marshal(outMsgJavaObj, outDocument);

            // The following should only be changed if not propagating message
            // to
            // the node's 'out' terminal
            out.propagate(outAssembly);
        } catch (MbException e) {
            // Re-throw to allow Broker handling of MbException
            throw e;
        } catch (RuntimeException e) {
            // Re-throw to allow Broker handling of RuntimeException
            throw e;
        } catch (Exception e) {
            // Consider replacing Exception with type(s) thrown by user code
            // Example handling ensures all exceptions are re-thrown to be
            // handled in the flow
            throw new MbUserException(this, "evaluate()", "", "", e.toString(), null);
        }
        // The following should only be changed
        // if not propagating message to the 'out' terminal
        out.propagate(outAssembly);

    }

}
