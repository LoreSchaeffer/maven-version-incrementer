package it.multicoredev.mvi;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Copyright © 2022 by Lorenzo Magni
 * This file is part of maven-property-incrementer.
 * maven-property-incrementer is under "The 3-Clause BSD License", you can find a copy <a href="https://opensource.org/licenses/BSD-3-Clause">here</a>.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */
@Mojo(name = "increment-build-remote", defaultPhase = LifecyclePhase.INITIALIZE, requiresProject = true, requiresDirectInvocation = true, aggregator = true, threadSafe = true)
public class MBIRemoteMojo extends AbstractMojo {

    @Parameter(property = "project")
    MavenProject project;

    @Parameter(property = "remote.get-url")
    String remoteGetUrl;

    @Parameter(property = "remote.set-url")
    String remoteSetUrl;

    @Parameter(property = "remote.get-body")
    String remoteGetBody;

    @Parameter(property = "remote.set-body")
    String remoteSetBody;

    public void execute() throws MojoExecutionException {
        if (remoteGetUrl == null || remoteGetUrl.trim().isEmpty()) throw new MojoExecutionException("remote.get-url is not set");
        if (remoteSetUrl == null || remoteSetUrl.trim().isEmpty()) throw new MojoExecutionException("remote.post-url is not set");

        int latestBuildNumber = getLatestBuildNumber() + 1;

        Document pom = readPOM();

        NodeList propertiesSearch = pom.getElementsByTagName("properties");

        if (propertiesSearch.getLength() > 0) {
            Node buildNode = null;

            NodeList properties = propertiesSearch.item(0).getChildNodes();
            for (int i = 0; i < properties.getLength(); i++) {
                Node property = properties.item(i);
                if (property.getNodeName().equals("build-number")) {
                    buildNode = property;
                    break;
                }
            }

            if (buildNode == null) {
                buildNode = pom.createElement("build-number");
                propertiesSearch.item(0).appendChild(buildNode);
            }

            buildNode.setTextContent(String.valueOf(latestBuildNumber));

            setLatestBuildNumber(latestBuildNumber);
        } else {
            Element properties = pom.createElement("properties");
            Element buildNumber = pom.createElement("build-number");
            buildNumber.setTextContent("1");
            properties.appendChild(buildNumber);
        }

        writePOM(pom);
    }

    private int getLatestBuildNumber() throws MojoExecutionException {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(remoteGetUrl).openConnection();
            connection.setRequestMethod("POST");

            if (remoteGetBody != null && !remoteGetBody.trim().isEmpty()) {
                connection.setDoOutput(true);
                try (BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream())) {
                    out.write(remoteGetBody.getBytes());
                }
            }

            connection.connect();

            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = in.readLine()) != null) response.append(line);

                try {
                    return Integer.parseInt(response.toString());
                } catch (NumberFormatException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void setLatestBuildNumber(int buildNumber) throws MojoExecutionException {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(remoteSetUrl).openConnection();
            connection.setRequestMethod("POST");

            if (remoteSetBody != null && !remoteSetBody.trim().isEmpty()) {
                connection.setDoOutput(true);
                try (BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream())) {
                    out.write(remoteSetBody.replace("{buildNumber}", String.valueOf(buildNumber)).getBytes());
                }
            }

            connection.connect();
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private Document readPOM() throws MojoExecutionException {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();

        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();

            return builder.parse(project.getFile());
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void writePOM(Document pom) throws MojoExecutionException {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            Result out = new StreamResult(project.getFile());
            Source in = new DOMSource(pom);
            transformer.transform(in, out);
        } catch (TransformerException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
