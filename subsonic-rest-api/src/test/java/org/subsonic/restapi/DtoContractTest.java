package org.subsonic.restapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.io.StringWriter;
import org.eclipse.persistence.jaxb.JAXBContextFactory;
import org.eclipse.persistence.jaxb.MarshallerProperties;
import org.junit.jupiter.api.Test;

class DtoContractTest {

    private static jakarta.xml.bind.JAXBContext createMoxyContext() throws Exception {
        return new JAXBContextFactory().createContext(new Class[] {Response.class}, null);
    }

    @Test
    void marshalsSubsonicResponseWithStableRootAndNamespace() throws Exception {
        Response response = new Response();
        response.setStatus(ResponseStatus.OK);
        response.setVersion("1.15.0");

        Child song = new Child();
        song.setId("song-1");
        song.setTitle("Example");
        song.setIsDir(false);
        response.setSong(song);

        StringWriter output = new StringWriter();
        Marshaller marshaller = createMoxyContext().createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, false);
        marshaller.marshal(response, output);

        String xml = output.toString();
        assertTrue(xml.contains("<subsonic-response"));
        assertTrue(xml.contains("xmlns=\"http://subsonic.org/restapi\""));
        assertTrue(xml.contains("status=\"ok\""));
        assertTrue(xml.contains("version=\"1.15.0\""));
        assertTrue(xml.contains("id=\"song-1\""));
        assertTrue(xml.contains("title=\"Example\""));
        assertTrue(xml.contains("isDir=\"false\""));
        assertFalse(xml.contains("parent=\""));
        assertFalse(xml.contains("isVideo=\""));
    }

    @Test
    void unmarshalsResponseAndKeepsLiveListAndJavaBeanApi() throws Exception {
        String xml = """
            <subsonic-response xmlns="http://subsonic.org/restapi" status="ok" version="1.15.0">
              <songsByGenre><song id="song-1" isDir="false" title="Example"/></songsByGenre>
            </subsonic-response>
            """;

        Unmarshaller unmarshaller = createMoxyContext().createUnmarshaller();
        Response response = (Response) unmarshaller.unmarshal(new StringReader(xml));

        assertEquals(ResponseStatus.OK, response.getStatus());
        assertNotNull(response.getSongsByGenre());
        assertEquals(1, response.getSongsByGenre().getSong().size());
        assertEquals("song-1", response.getSongsByGenre().getSong().get(0).getId());
        assertFalse(response.getSongsByGenre().getSong().get(0).isIsDir());
        assertEquals(MediaType.MUSIC, MediaType.fromValue(MediaType.MUSIC.value()));
        assertEquals("new", PodcastStatus.NEW.value());
    }

    @Test
    void moxyJsonKeepsTheSubsonicResponseRoot() throws Exception {
        Response response = new Response();
        response.setStatus(ResponseStatus.OK);
        response.setVersion("1.15.0");

        Marshaller marshaller = createMoxyContext().createMarshaller();
        marshaller.setProperty(MarshallerProperties.MEDIA_TYPE, "application/json");
        marshaller.setProperty(MarshallerProperties.JSON_INCLUDE_ROOT, true);

        StringWriter output = new StringWriter();
        marshaller.marshal(new ObjectFactory().createSubsonicResponse(response), output);

        assertTrue(output.toString().contains("\"subsonic-response\""));
        assertTrue(output.toString().contains("\"status\":\"ok\""));
    }

    @Test
    void objectFactoryRetainsElementAdapter() {
        ObjectFactory factory = new ObjectFactory();
        Response response = factory.createResponse();
        assertNotNull(factory.createSubsonicResponse(response));
        assertEquals(Response.class, factory.createSubsonicResponse(response).getDeclaredType());
    }
}
