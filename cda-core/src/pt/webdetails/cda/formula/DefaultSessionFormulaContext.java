/*!
* Copyright 2002 - 2013 Webdetails, a Pentaho company.  All rights reserved.
* 
* This software was developed by Webdetails and is provided under the terms
* of the Mozilla Public License, Version 2.0, or any later version. You may not use
* this file except in compliance with the license. If you need a copy of the license,
* please go to  http://mozilla.org/MPL/2.0/. The Initial Developer is Webdetails.
*
* Software distributed under the Mozilla Public License is distributed on an "AS IS"
* basis, WITHOUT WARRANTY OF ANY KIND, either express or  implied. Please refer to
* the license for the specific language governing your rights and limitations.
*/

package pt.webdetails.cda.formula;

import java.util.HashMap;
import java.util.Map;

import org.pentaho.reporting.libraries.base.config.Configuration;
import org.pentaho.reporting.libraries.formula.ContextEvaluationException;
import org.pentaho.reporting.libraries.formula.DefaultFormulaContext;
import org.pentaho.reporting.libraries.formula.LocalizationContext;
import org.pentaho.reporting.libraries.formula.function.FunctionRegistry;
import org.pentaho.reporting.libraries.formula.operators.OperatorFactory;
import org.pentaho.reporting.libraries.formula.typing.Type;
import org.pentaho.reporting.libraries.formula.typing.TypeRegistry;

import pt.webdetails.cda.CdaEngine;
import pt.webdetails.cpf.session.ISessionUtils;
import pt.webdetails.cpf.session.IUserSession;

public class DefaultSessionFormulaContext implements
		ICdaCoreSessionFormulaContext {

	private DefaultFormulaContext df = new DefaultFormulaContext();;
	private Map<String, ICdaParameterProvider> providers = new HashMap<String, ICdaParameterProvider>();
	
	
	public DefaultSessionFormulaContext(Map<String, ICdaParameterProvider> ps) {
		if (ps == null || ps.size() == 0) {
			ISessionUtils utils = CdaEngine.getEnvironment().getSessionUtils();
			if (utils != null) {
				this.providers.put("security:", new CdaSecurityParameterProvider(utils));
				this.providers.put("session:", new CdaSessionParameterProvider(utils));
			}
			this.providers.put("system:", new CdaSystemParameterProvider());
		} else {
			this.providers = ps;
		}
	}
	
	public void setProviders(Map<String, ICdaParameterProvider> _providers) {
		if (_providers != null) {
			this.providers.putAll(_providers);
		}
	}
	
	@Override
	public Configuration getConfiguration() {
		return df.getConfiguration();
	}

	@Override
	public FunctionRegistry getFunctionRegistry() {
		return df.getFunctionRegistry();
	}

	@Override
	public LocalizationContext getLocalizationContext() {
		return df.getLocalizationContext();
	}

	@Override
	public OperatorFactory getOperatorFactory() {
		return df.getOperatorFactory();
	}

	@Override
	public TypeRegistry getTypeRegistry() {
		return df.getTypeRegistry();
	}

	@Override
	public boolean isReferenceDirty(Object name) throws ContextEvaluationException {
		return df.isReferenceDirty(name);
	}

	@Override
	public Type resolveReferenceType(Object name) throws ContextEvaluationException {
		return df.resolveReferenceType(name);
	}
	
    @Override
    public Object resolveReference(final Object name)
    {
      if (name instanceof String)
      {
        String paramName = ((String) name).trim();
        for (String prefix : providers.keySet())
        {
          if (paramName.startsWith(prefix))
          {
        	//logger.debug("Found provider for prefix: " + prefix + " Provider: " + providers.get(prefix));
            paramName = paramName.substring(prefix.length());
            Object value = providers.get(prefix).getParameter(paramName);
            return value;
          }
        }
      }
      return df.resolveReference(name);
    }


	@Override
	public void setSession(IUserSession session) {
		// not sure if we need it?
	}

}
