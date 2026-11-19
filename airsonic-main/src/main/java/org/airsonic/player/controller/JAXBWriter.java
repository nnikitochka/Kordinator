package org.airsonic.player.controller;

import com.google.common.net.MediaType;
import org.airsonic.player.controller.rest.error.ErrorCode;
import org.airsonic.player.controller.rest.error.APIException;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.jdom2.Attribute;
import org.jdom2.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subsonic.restapi.Error;
import org.subsonic.restapi.ObjectFactory;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.ResponseStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.Instant;
import java.util.GregorianCalendar;
import java.util.Map.Entry;

import static org.airsonic.player.util.XMLUtil.createSAXBuilder;
public class JAXBWriter {
    private static final Logger LOG = LoggerFactory.getLogger(JAXBWriter.class);

    private final jakarta.xml.bind.JAXBContext jaxbContext;
    private final DatatypeFactory datatypeFactory;
    private static final String restProtocolVersion = parseRESTProtocolVersion();

    public JAXBWriter() {
        try {
            // Deterministically use MOXy (needed for the JSON media-type properties below);
            // don't rely on the ServiceLoader, as another jakarta JAXB provider may be on the
            // classpath (ehcache:jakarta pulls in glassfish jaxb-runtime).
            jaxbContext = JAXBContextFactory.createContext(new Class[] {Response.class}, null);
            datatypeFactory = DatatypeFactory.newInstance();
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    private Marshaller createXmlMarshaller() {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, StringUtil.ENCODING_UTF8);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            return marshaller;
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    private Marshaller createJsonMarshaller() {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, StringUtil.ENCODING_UTF8);
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.setProperty(MarshallerProperties.MEDIA_TYPE, "application/json");
            marshaller.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, true);
            return marshaller;
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }
    }

    private static String parseRESTProtocolVersion() {
        try (InputStream in = StringUtil.class.getResourceAsStream("/subsonic-rest-api.xsd")) {
            Document document = createSAXBuilder().build(in);
            Attribute version = document.getRootElement().getAttribute("version");
            return version.getValue();
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    public static String getRestProtocolVersion() {
        return restProtocolVersion;
    }

    public Response createResponse(boolean ok) {
        Response response = new ObjectFactory().createResponse();
        response.setStatus(ok ? ResponseStatus.OK : ResponseStatus.FAILED);
        response.setVersion(restProtocolVersion);
        return response;
    }

    public void writeResponse(HttpServletRequest request, HttpServletResponse httpResponse, Response jaxbResponse) {
        Entry<String, String> serializedResp = serializeForType(request, jaxbResponse);

        httpResponse.setCharacterEncoding(StringUtil.ENCODING_UTF8);
        httpResponse.setContentType(serializedResp.getKey());

        try {
            httpResponse.getWriter().append(serializedResp.getValue());
        } catch (IOException x) {
            LOG.error("Failed to marshal JAXB", x);
            throw new RuntimeException(x);
        }
    }

    public void writeErrorResponse(HttpServletRequest request, HttpServletResponse response,
                                   ErrorCode code, String message) {
        Response res = createErrorResponse(code, message);
        writeResponse(request, response, res);
    }

    public Response createErrorResponse(APIException e) {
        return createErrorResponse(e.getError(), e.getMessage());
    }

    public Response createErrorResponse(ErrorCode code, String message) {
        Response res = createResponse(false);
        Error error = new Error();
        res.setError(error);
        error.setCode(code.getCode());
        error.setMessage(message);
        return res;
    }

    public Entry<String, String> serializeForType(HttpServletRequest request, Response resp) {
        String format = request.getParameter("f");
        if (format == null) {
            format = "xml";
        }
        String jsonpCallback = request.getParameter("callback");
        return serializeForType(format, jsonpCallback, resp);
    }

    public Entry<String, String> serializeForType(String format, String jsonpCallback, Response resp) {
        boolean json = "json".equals(format);
        boolean jsonp = "jsonp".equals(format) && jsonpCallback != null;
        Marshaller marshaller;
        MediaType type;

        if (json) {
            marshaller = createJsonMarshaller();
            type = MediaType.JSON_UTF_8;
        } else if (jsonp) {
            marshaller = createJsonMarshaller();
            type = MediaType.JAVASCRIPT_UTF_8;
        } else {
            marshaller = createXmlMarshaller();
            type = MediaType.XML_UTF_8;
        }

        StringWriter writer = new StringWriter();
        try {
            if (jsonp) {
                writer.append(jsonpCallback).append('(');
            }
            marshaller.marshal(new ObjectFactory().createSubsonicResponse(resp), writer);
            if (jsonp) {
                writer.append(");");
            }
        } catch (JAXBException x) {
            LOG.error("Failed to marshal JAXB", x);
            throw new RuntimeException(x);
        }

        return Pair.of(type.toString(), writer.toString());
    }

    public XMLGregorianCalendar convertDate(Instant date) {
        if (date == null) {
            return null;
        }

        GregorianCalendar c = new GregorianCalendar();
        c.setTimeInMillis(date.toEpochMilli());
        return datatypeFactory.newXMLGregorianCalendar(c).normalize();
    }
}
