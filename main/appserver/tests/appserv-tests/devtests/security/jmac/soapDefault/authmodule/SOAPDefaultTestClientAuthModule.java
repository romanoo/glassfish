/*
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.s1asdev.security.jmac.soapdefault;

import java.util.Map;
import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.security.auth.message.MessagePolicy;
import javax.security.auth.message.module.ClientAuthModule;
import javax.xml.soap.SOAPMessage;

public class SOAPDefaultTestClientAuthModule implements ClientAuthModule {
    private CallbackHandler handler = null;

    public void initialize(MessagePolicy requestPolicy,
               MessagePolicy responsePolicy,
               CallbackHandler handler,
               Map options)
               throws AuthException {
        this.handler = handler;
    }

    public Class[] getSupportedMessageTypes() {
        return new Class[] { SOAPMessage.class };
    }

    public AuthStatus secureRequest(MessageInfo messageInfo,
            Subject clientSubject) throws AuthException {
        SOAPMessage reqMessage = (SOAPMessage)messageInfo.getRequestMessage();
        try {
            Util.prependSOAPMessage(reqMessage, "SecReq ");
        } catch(Exception ex) {
            AuthException aex = new AuthException();
            aex.initCause(ex);
            throw aex;
        }
        return AuthStatus.SUCCESS;
    }

    public AuthStatus validateResponse(MessageInfo messageInfo,
            Subject clientSubject, Subject serviceSubject)
            throws AuthException {
        SOAPMessage respMessage = (SOAPMessage)messageInfo.getResponseMessage();
        try {
            String value = Util.getValue(respMessage);
            if (value == null || !value.startsWith("SecResp ") ||
                    (value.indexOf("ValReq SecReq ") == -1)) {
                return AuthStatus.FAILURE;
            }
            Util.prependSOAPMessage(respMessage, "ValResp ");
        } catch(Exception ex) {
            AuthException aex = new AuthException();
            aex.initCause(ex);
            throw aex;
        }
        return AuthStatus.SUCCESS;
    }

    public void cleanSubject(MessageInfo messageInfo, Subject subject)
        throws AuthException {
    }
}
