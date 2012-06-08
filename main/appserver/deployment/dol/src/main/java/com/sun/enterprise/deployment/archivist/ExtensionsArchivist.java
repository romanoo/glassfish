/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.enterprise.deployment.archivist;

import com.sun.enterprise.deployment.io.DeploymentDescriptorFile;
import org.glassfish.api.deployment.archive.ArchiveType;
import org.glassfish.deployment.common.RootDeploymentDescriptor;
import com.sun.enterprise.deployment.BundleDescriptor;
import com.sun.enterprise.deployment.annotation.impl.ModuleScanner;
import com.sun.enterprise.deployment.util.DOLUtils;
import com.sun.logging.LogDomains;
import org.glassfish.api.deployment.archive.ReadableArchive;
import org.glassfish.api.deployment.archive.WritableArchive;
import org.glassfish.deployment.common.DeploymentUtils;
import org.xml.sax.SAXParseException;
import org.jvnet.hk2.annotations.Contract;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;

/**
 * An extension archivist is processing extensions deployment descriptors like
 * web services, persistence or even EJB information within a war file.
 *
 * They do not represent a top level archivist, as it is not capable of loading
 * BundleDescriptors directly but require a top level archivist to do so before
 * they can process their own metadata
 *
 * @author Jerome Dochez
 */
@Contract
public abstract class ExtensionsArchivist  {

    protected final Logger logger = LogDomains.getLogger(DeploymentUtils.class, LogDomains.DPL_LOGGER);

    // configuration DD files associated with this archivist
    protected List<DeploymentDescriptorFile> confDDFiles;

    // the sorted configuration DD files with precedence from
    // high to low
    private List<DeploymentDescriptorFile> sortedConfDDFiles;

    // configuration DD file that will be used
    private DeploymentDescriptorFile confDD;

    /**
     * @return the DeploymentDescriptorFile responsible for handling
     *         standard deployment descriptor
     */
    public abstract DeploymentDescriptorFile getStandardDDFile(RootDeploymentDescriptor descriptor);

    /**
     * @return the list of the DeploymentDescriptorFile responsible for
     *         handling the configuration deployment descriptors
     */
    public abstract List<DeploymentDescriptorFile> getConfigurationDDFiles(RootDeploymentDescriptor descriptor);

    /**
     * @return if exists the DeploymentDescriptorFile responsible for
     *         handling the configuration deployment descriptors
     */
    public DeploymentDescriptorFile getConfigurationDDFile(RootDeploymentDescriptor descriptor, ReadableArchive archive) throws IOException {
        if (confDD == null) {
            getSortedConfigurationDDFiles(descriptor, archive);
            if (sortedConfDDFiles != null && !sortedConfDDFiles.isEmpty()) {
               confDD = sortedConfDDFiles.get(0);
            }
        }
        return confDD;
    }

    /**
     * @param the moduleType
     * @return whether this extension archivist supports this module type 
     *
     */
    public abstract boolean supportsModuleType(ArchiveType moduleType);

    /**
     * @return a default Descriptor for this archivist
     */
    public abstract <T extends RootDeploymentDescriptor> T getDefaultDescriptor();

    /**
     * Returns the scanner for this archivist
     *
     */
    public ModuleScanner getScanner() {
        return null;
    }

    /**
     * Add the extension descriptor to the main descriptor
     *
     * @param root the main descriptor
     * @param extension the extension descriptor
     * @return the main descriptor with the extensions
     */
    public <T extends RootDeploymentDescriptor> void addExtension(RootDeploymentDescriptor root, RootDeploymentDescriptor extension) {
        root.addExtensionDescriptor(extension.getClass(), extension, null);
        extension.setModuleDescriptor(root.getModuleDescriptor());
    }

   /**
     * Read the standard deployment descriptor of the extension
     * @param archivist the primary archivist for this archive 
     * @param archive the archive
     * @param descriptor the main deployment descriptor
     * @return the extension descriptor object
     *
     */
    public Object open(Archivist main, ReadableArchive archive, RootDeploymentDescriptor descriptor)
            throws IOException, SAXParseException {


        DeploymentDescriptorFile confDD = getStandardDDFile(descriptor);
         if (archive.getURI() != null) {
             confDD.setErrorReportingString(archive.getURI().getSchemeSpecificPart());
         }
         InputStream is = null;
         try {
             is = archive.getEntry(confDD.getDeploymentDescriptorPath());
             if (is == null) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "Deployment descriptor: " +
                                    confDD.getDeploymentDescriptorPath(),
                                    " does not exist in archive: " + 
                                    archive.getURI().getSchemeSpecificPart());
                }

             } else {
                 confDD.setXMLValidation(main.getXMLValidation());
                 confDD.setXMLValidationLevel(main.getXMLValidationLevel());
                 return confDD.read(descriptor, is);
             }
         } finally {
             if (is != null) {
                 is.close();
             }
         }
         return null;
     }

    /**
     * Read the runtime deployment descriptors of the extension
     *
     * @param archivist the primary archivist for this archive 
     * @param archive the archive
     * @param descriptor the extension deployment descriptor
     * @return the extension descriptor object with additional runtime information
     */
     public Object readRuntimeDeploymentDescriptor(Archivist main, ReadableArchive archive, RootDeploymentDescriptor descriptor)
            throws IOException, SAXParseException {

        DeploymentDescriptorFile ddFile = getConfigurationDDFile(descriptor, archive);

        // if this extension archivist has no runtime DD, just return the 
        // original descriptor
        if (ddFile == null) {
            return descriptor;
        }

        DOLUtils.readRuntimeDeploymentDescriptor(getSortedConfigurationDDFiles(descriptor, archive), archive, descriptor, main,true);

        return descriptor;
    }

    /**
     * writes the deployment descriptors (standard and runtime)
     * to a JarFile using the right deployment descriptor path
     *
     * @param in the input archive
     * @param out the abstract archive file to write to
     */
    public void writeDeploymentDescriptors(BundleDescriptor descriptor, ReadableArchive in, WritableArchive out) throws IOException {

        // Standard DDs
        writeStandardDeploymentDescriptors(descriptor, out);

        // Runtime DDs
        writeRuntimeDeploymentDescriptors(descriptor, in, out);
    }

    /**
     * writes the standard deployment descriptors to an abstract archive
     *
     * @param out archive to write to
     */
    public void writeStandardDeploymentDescriptors(BundleDescriptor descriptor, WritableArchive out) throws IOException {

        OutputStream os = out.putNextEntry(getStandardDDFile(descriptor).getDeploymentDescriptorPath());
        getStandardDDFile(descriptor).write(descriptor, os);
        out.closeEntry();
    }

    /**
     * writes the runtime deployment descriptors to an abstract archive
     *
     * @param in the input archive
     * @param out output archive
     */
    public void writeRuntimeDeploymentDescriptors(BundleDescriptor descriptor, ReadableArchive in, WritableArchive out) throws IOException {

        // when source archive contains runtime deployment descriptor
        // files, write those out
        // otherwise write all possible runtime deployment descriptor
        // files out
        List<DeploymentDescriptorFile> confDDFilesToWrite = getSortedConfigurationDDFiles(descriptor, in);
        if (confDDFilesToWrite.isEmpty()) {
            confDDFilesToWrite = getConfigurationDDFiles(descriptor);
        }
        for (DeploymentDescriptorFile ddFile : confDDFilesToWrite) {
            OutputStream os = out.putNextEntry(
                ddFile.getDeploymentDescriptorPath());
            ddFile.write(descriptor, os);
            out.closeEntry();
        }
    }

    private List<DeploymentDescriptorFile> getSortedConfigurationDDFiles(RootDeploymentDescriptor descriptor, ReadableArchive archive) throws IOException {
        if (sortedConfDDFiles == null) {
            sortedConfDDFiles = DOLUtils.processConfigurationDDFiles(getConfigurationDDFiles(descriptor), archive);
        }
        return sortedConfDDFiles;
    }
}
