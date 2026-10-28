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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.spring;

import org.airsonic.player.filter.ContentSecurityPolicyFilter;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.IWebContext;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.IProcessor;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;
import org.thymeleaf.templatemode.TemplateMode;

import java.util.Set;

/**
 * Adds the Content-Security-Policy nonce of the current request to every {@code <script>} element
 * of the HTML templates, so that templates never have to remember it.
 */
public class CspNonceDialect extends AbstractProcessorDialect {

    public CspNonceDialect() {
        super("Content-Security-Policy nonce", "csp", 1000);
    }

    @Override
    public Set<IProcessor> getProcessors(String dialectPrefix) {
        return Set.of(new ScriptNonceProcessor(dialectPrefix));
    }

    static class ScriptNonceProcessor extends AbstractElementTagProcessor {

        ScriptNonceProcessor(String dialectPrefix) {
            // matches the plain element name, not a prefixed one
            super(TemplateMode.HTML, dialectPrefix, "script", false, null, false, 1000);
        }

        @Override
        protected void doProcess(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
            if (context instanceof IWebContext webContext) {
                Object nonce = webContext.getExchange().getAttributeValue(ContentSecurityPolicyFilter.NONCE_ATTRIBUTE);
                if (nonce != null) {
                    structureHandler.setAttribute("nonce", nonce.toString());
                }
            }
        }
    }
}
