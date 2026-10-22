/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.removal;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * PLANNED REMOVAL. Kept only so that {@code /rest/exportPodcasts/opml} can still answer
 * with a valid, empty OPML document. Delete together with {@link PodcastRemovalController}.
 */
@XmlRootElement(name = "opml")
public class PodcastExportOPML {
    private String version = "2.0";
    private Head head = new Head();
    private Body body = new Body();

    @XmlAttribute
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @XmlElement
    public Head getHead() {
        return head;
    }

    public void setHead(Head head) {
        this.head = head;
    }

    @XmlElement
    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    public static class Head {
        private String title = "Airsonic Exported OPML";
        private String dateCreated = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME);

        @XmlElement
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @XmlElement
        public String getDateCreated() {
            return dateCreated;
        }

        public void setDateCreated(String dateCreated) {
            this.dateCreated = dateCreated;
        }
    }

    public static class Body {
        public Body() {
            Outline o = new Outline();
            o.setType(null);
            o.setTitle("Airsonic Exported OPML");
            o.setText("Airsonic Exported OPML");
            outline.add(o);
        }

        private List<Outline> outline = new ArrayList<>();

        @XmlElement
        public List<Outline> getOutline() {
            return outline;
        }

        public void setOutline(List<Outline> outline) {
            this.outline = outline;
        }
    }

    public static class Outline {
        private String type = "rss";
        private String xmlUrl;
        private String title;
        private String text;
        private List<Outline> outline = new ArrayList<>();

        @XmlAttribute
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @XmlAttribute
        public String getXmlUrl() {
            return xmlUrl;
        }

        public void setXmlUrl(String xmlUrl) {
            this.xmlUrl = xmlUrl;
        }

        @XmlAttribute
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @XmlAttribute
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @XmlElement
        public List<Outline> getOutline() {
            return outline;
        }

        public void setOutline(List<Outline> outline) {
            this.outline = outline;
        }

    }

}
