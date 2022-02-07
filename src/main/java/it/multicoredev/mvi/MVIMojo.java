package it.multicoredev.mvi;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
@Mojo(name = "increment-version", defaultPhase = LifecyclePhase.PACKAGE, requiresProject = true, requiresDirectInvocation = true, aggregator = true, threadSafe = true)
public class MVIMojo extends AbstractMojo {

    @Parameter(property = "project")
    MavenProject project;

    @Parameter(property = "version.format", defaultValue = "[0-9]+")
    String format;

    public void execute() throws MojoExecutionException {
        String version = project.getVersion();
        Document pom = readPOM();

        NodeList versionSearch = pom.getElementsByTagName("version");
        if (versionSearch.getLength() == 0) throw new MojoExecutionException("No version found in pom.xml");

        Node versionNode = versionSearch.item(0);

        Matcher matcher = Pattern.compile(format).matcher(version);

        int lastIndex = -1;
        String lastMatch = null;

        while (matcher.find()) {
            lastIndex = matcher.start();
            lastMatch = matcher.group();
        }

        if (lastIndex == -1 || lastMatch == null) throw new MojoExecutionException("Invalid version format");

        int buildNumber;
        try {
            buildNumber = Integer.parseInt(lastMatch);
        } catch (NumberFormatException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        buildNumber++;

        version = version.substring(0, lastIndex) + buildNumber + version.substring(lastIndex + lastMatch.length());
        project.setVersion(version);
        versionNode.setTextContent(version);

        writePOM(pom);
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
