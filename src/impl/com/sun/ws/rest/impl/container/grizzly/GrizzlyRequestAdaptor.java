/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved. 
 * 
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License. 
 * 
 * You can obtain a copy of the License at:
 *     https://jersey.dev.java.net/license.txt
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each
 * file and include the License file at:
 *     https://jersey.dev.java.net/license.txt
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 *     "Portions Copyrighted [year] [name of copyright owner]"
 */

package com.sun.ws.rest.impl.container.grizzly;

import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.buf.B2CConverter;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.CharChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.http.MimeHeaders;
import com.sun.grizzly.util.http.Parameters;
import com.sun.ws.rest.spi.container.AbstractContainerRequest;
import com.sun.ws.rest.impl.http.header.HttpHeaderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import javax.ws.rs.core.MultivaluedMap;

/**
 *
 * @author Marc.Hadley@Sun.Com
 */
public final class GrizzlyRequestAdaptor  extends AbstractContainerRequest {
    
    private final Request request;
    
    /** Creates a new instance of GrizzlyRequestAdaptor */
    public GrizzlyRequestAdaptor(Request request) {
        super(request.method().toString(), new GrizzlyRequestInputStream(request));
        this.request = request;
        
        initiateUriInfo();
        copyHttpHeaders();
    }

    private void initiateUriInfo() {
        try {
            this.baseUri = new URI(
                    request.scheme().toString(), 
                    null,
                    request.serverName().toString(), 
                    request.getServerPort(),
                    "/", 
                    null, 
                    null);

            /*
             * request.unparsedURI() is a URI in encoded form that contains
             * the URI path and URI query components.
             */
            this.completeUri = baseUri.resolve(request.unparsedURI().toString());
        } catch (URISyntaxException ex) {
            ex.printStackTrace();
            throw new IllegalArgumentException(ex);
        }
    }
    
    private void copyHttpHeaders() {
        MultivaluedMap<String, String> headers = getRequestHeaders();
        
        MimeHeaders mh = request.getMimeHeaders();
        Enumeration names = mh.names();
        while (names.hasMoreElements()) {
            String name = (String)names.nextElement();
            String value = mh.getHeader(name);
            headers.add(name, value);
            if (name.equalsIgnoreCase("cookie")) {
                getCookies().putAll(HttpHeaderFactory.createCookies(value));
            }
        }
    }
    
    private static final class GrizzlyRequestInputStream extends InputStream {
        
        private final Request request;
        private final ByteChunk chunk;
        private ByteArrayInputStream stream;
        
        public GrizzlyRequestInputStream(Request request) {
            this.request = request;
            this.chunk = new ByteChunk();
        }

        public int read() throws IOException {
            refillIfRequired();
            return stream.read();
        }
        
        public int read(byte[] b) throws IOException {
            refillIfRequired();
            return stream.read(b);
        }
        
        private void refillIfRequired() throws IOException {
            if (stream==null || stream.available()==0) {
                //chunk.recycle();
                request.doRead(chunk);
                if (chunk.getLength() > 0)
                    stream = new ByteArrayInputStream(chunk.getBytes(), chunk.getStart(), chunk.getLength());
            }
        }
    }
}
