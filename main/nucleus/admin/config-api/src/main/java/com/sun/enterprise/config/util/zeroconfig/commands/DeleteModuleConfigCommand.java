/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.enterprise.config.util.zeroconfig.commands;

import com.sun.enterprise.config.serverbeans.Config;
import com.sun.enterprise.config.serverbeans.Domain;
import com.sun.enterprise.config.serverbeans.DomainExtension;
import com.sun.enterprise.config.util.zeroconfig.ZeroConfigUtils;
import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.util.SystemPropertyConstants;
import org.glassfish.api.ActionReport;
import org.glassfish.api.I18n;
import org.glassfish.api.Param;
import org.glassfish.api.admin.AdminCommand;
import org.glassfish.api.admin.AdminCommandContext;
import org.glassfish.api.admin.ExecuteOn;
import org.glassfish.api.admin.RuntimeType;
import org.glassfish.api.admin.config.ConfigExtension;
import org.glassfish.api.admin.config.Container;
import org.glassfish.config.support.CommandTarget;
import org.glassfish.config.support.GlassFishConfigBean;
import org.glassfish.config.support.TargetType;
import org.jvnet.hk2.annotations.Scoped;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.component.Habitat;
import org.glassfish.hk2.api.PerLookup;
import org.jvnet.hk2.config.ConfigSupport;
import org.jvnet.hk2.config.SingleConfigCode;
import org.jvnet.hk2.config.TransactionFailure;

import javax.inject.Inject;
import java.beans.PropertyVetoException;
import java.util.List;
import java.util.logging.Logger;


/**
 * A remote command to delete a module's configuration.
 *
 * @author Masoud Kalali
 */
@TargetType(value = {CommandTarget.DAS, CommandTarget.DOMAIN, CommandTarget.CLUSTER, CommandTarget.STANDALONE_INSTANCE})
@ExecuteOn(RuntimeType.ALL)
@Service(name = "delete-module-config")
@PerLookup
@I18n("delete.module.config")
public final class DeleteModuleConfigCommand implements AdminCommand {
    private final Logger LOG = Logger.getLogger(DeleteModuleConfigCommand.class.getName());
    final private static LocalStringManagerImpl localStrings =
            new LocalStringManagerImpl(DeleteModuleConfigCommand.class);
    private static final String DEFAULT_FORMAT = "";

    @Inject
    private Domain domain;

    @Inject
    Habitat habitat;

    @Param(optional = true, defaultValue = SystemPropertyConstants.DAS_SERVER_CONFIG, name = "target")
    private String target;

    @Param(name = "serviceName", primary = true)
    private String serviceName;

    @Override
    public void execute(AdminCommandContext context) {
        final ActionReport report = context.getActionReport();
        Config config = domain.getConfigNamed(target);

        if (serviceName == null) {
            report.setMessage(localStrings.getLocalString("delete.module.config.service.name.is.required",
                    "The service name is required, please specify which service you want to delete its default configuration"));
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            return;
        }

        final String className = ZeroConfigUtils.convertConfigElementNameToClassNAme(serviceName);
        Class configBeanType = ZeroConfigUtils.getClassFor(serviceName, habitat);
        if (configBeanType == null) {
            String msg = localStrings.getLocalString("delete.module.config.not.such.a.service.found",
                    DEFAULT_FORMAT);
            report.setActionExitCode(ActionReport.ExitCode.FAILURE);
            report.setMessage(msg);
            return;
        }

        if (ConfigExtension.class.isAssignableFrom(configBeanType)) {
            if (config.checkIfConfigExists(configBeanType)) {
                try {
                    ConfigSupport.apply(new SingleConfigCode<Config>() {
                        @Override
                        public Object run(Config param) throws PropertyVetoException,
                                TransactionFailure {
                            List<Container> containers;
                            containers = param.getContainers();
                            for (Container cont : containers) {
                                String containerClassName = GlassFishConfigBean.unwrap(cont).getProxyType().getSimpleName();
                                if (containerClassName.equals(className)) {
                                    containers.remove(cont);
                                    break;
                                }
                            }

                            return containers;
                        }
                    }, config);
                    report.setActionExitCode(ActionReport.ExitCode.SUCCESS);
                } catch (TransactionFailure e) {
                    String actual = e.getMessage();
                    String msg = localStrings.getLocalString("delete.module.config.failed.to.delete.config",
                            DEFAULT_FORMAT, serviceName, actual);
                    report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                    report.setFailureCause(e);
                }
            } else {
                report.setMessage(localStrings.getLocalString("delete.module.config.no.configuration",
                        "No customized configuration exist for this service nor the default configuration has been added to the domain.xml."));
                report.setActionExitCode(ActionReport.ExitCode.FAILURE);
                return;
            }

        } else if (configBeanType.isAssignableFrom(DomainExtension.class)) {
            //TODO implement the deletion from the domain configuration
        }
    }
}
