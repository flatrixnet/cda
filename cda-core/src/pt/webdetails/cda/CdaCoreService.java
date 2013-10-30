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

package pt.webdetails.cda;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import pt.webdetails.cda.cache.ICacheScheduleManager;
import pt.webdetails.cda.cache.monitor.CacheMonitorHandler;
import pt.webdetails.cda.dataaccess.AbstractDataAccess;
import pt.webdetails.cda.dataaccess.DataAccessConnectionDescriptor;
import pt.webdetails.cda.discovery.DiscoveryOptions;
import pt.webdetails.cda.exporter.Exporter;
import pt.webdetails.cda.exporter.ExporterEngine;
import pt.webdetails.cda.query.QueryOptions;
import pt.webdetails.cda.settings.CdaSettings;
import pt.webdetails.cda.settings.SettingsManager;
import pt.webdetails.cda.utils.DoQueryParameters;
import pt.webdetails.cpf.http.ICommonParameterProvider;
import pt.webdetails.cpf.repository.IRepositoryAccess.FileAccess;
import pt.webdetails.cpf.repository.IRepositoryAccess;
import pt.webdetails.cpf.repository.IRepositoryFile;
import pt.webdetails.cpf.session.ISessionUtils;
import pt.webdetails.cpf.utils.MimeTypes;



public class CdaCoreService 
{ 

  private static Log logger = LogFactory.getLog(CdaCoreService.class);
  public static final String PLUGIN_NAME = "cda";
  private static final long serialVersionUID = 1L;
  
  @Deprecated
  private static final String EDITOR_SOURCE = "/editor/editor.html";
  @Deprecated
  private static final String EXT_EDITOR_SOURCE = "/editor/editor-cde.html";
  @Deprecated
  private static final String PREVIEWER_SOURCE = "/previewer/previewer.html";
  @Deprecated
  private static final String CACHE_MANAGER_PATH = "system/" + PLUGIN_NAME + "/cachemanager/cache.html";
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int DEFAULT_START_PAGE = 0;
  private static final String PREFIX_PARAMETER = "param";
  private static final String PREFIX_SETTING = "setting";
  private static final String JSONP_CALLBACK = "callback";
  public static final String ENCODING = "UTF-8";
  private IResponseTypeHandler responseHandler;

  //@Exposed(accessLevel = AccessLevel.PUBLIC)
 // @Audited(action = "doQuery")
  public CdaCoreService(){}
  public CdaCoreService(IResponseTypeHandler responseHandler){this.responseHandler = responseHandler;}
  public void setResponseHandler(IResponseTypeHandler responseHandler){this.responseHandler = responseHandler;}
  public void doQuery(final OutputStream out,DoQueryParameters parameters) throws Exception
  {        
    final CdaEngine engine = CdaEngine.getInstance();
    final QueryOptions queryOptions = new QueryOptions();

    final String path = getRelativePath(parameters.getPath() , parameters.getSolution(), parameters.getFile());
    final CdaSettings cdaSettings = SettingsManager.getInstance().parseSettingsFile(path);
    
    // Handle paging options
    // We assume that any paging options found mean that the user actively wants paging.
    final long pageSize = parameters.getPageSize();
    final long pageStart = parameters.getPageStart();
    final boolean paginate = parameters.isPaginateQuery();
    if (pageSize > 0 || pageStart > 0 || paginate)
    {
      if (pageSize > Integer.MAX_VALUE || pageStart > Integer.MAX_VALUE)
      {
        throw new ArithmeticException("Paging values too large");
      }
      queryOptions.setPaginate(true);
      queryOptions.setPageSize(pageSize > 0 ? (int) pageSize : paginate ? DEFAULT_PAGE_SIZE : 0);
      queryOptions.setPageStart(pageStart > 0 ? (int) pageStart : paginate ? DEFAULT_START_PAGE : 0);
    }
    
    // Support for bypassCache (we'll maintain the name we use in CDE
    /*if(requestParams.hasParameter("bypassCache")){
      queryOptions.setCacheBypass(Boolean.parseBoolean(requestParams.getStringParameter("bypassCache","false")));
    }*/
    
    queryOptions.setCacheBypass(parameters.isBypassCache());
    
    // Handle the query itself and its output format...
    queryOptions.setOutputType(parameters.getOutputType());
    queryOptions.setDataAccessId(parameters.getDataAccessId());
    try {
      queryOptions.setOutputIndexId(parameters.getOutputIndexId());
    } catch (NumberFormatException e) {
      logger.error("Illegal outputIndexId '" + parameters.getOutputIndexId() + "'" );
    }
    
    final ArrayList<String> sortBy = new ArrayList<String>();
    String[] def =
    {
    };
    for (Object obj : parameters.getSortBy())
    {
      if (!((String) obj).equals(""))
      {
        sortBy.add((String) obj);
      }
    }
    queryOptions.setSortBy(sortBy);


    // ... and the query parameters
    // We identify any pathParams starting with "param" as query parameters and extra settings prefixed with "setting"
    @SuppressWarnings("unchecked")
    final Iterator settings = parameters.getExtraSettings().entrySet().iterator();
    while (settings.hasNext())
    {
        Map.Entry<String,Object> pairs = (Map.Entry)settings.next();
      final String name = pairs.getKey();
      final Object parameter = pairs.getValue();
      queryOptions.addSetting(name, (String)parameter);
    }
    final Iterator params = parameters.getExtraParams().entrySet().iterator();
    while (params.hasNext())
    {
        
      Map.Entry<String,Object> pairs = (Map.Entry)params.next();
      final String name = pairs.getKey();
      final Object parameter = pairs.getValue();
      queryOptions.addParameter(name, parameter);

//      if(name.startsWith(PREFIX_PARAMETER)){
//          queryOptions.addParameter(name.substring(PREFIX_PARAMETER.length()), parameter);
//          logger.debug("##########"+name+"##############");
          
      }
/*
      if (param.startsWith(PREFIX_PARAMETER))
      {
        queryOptions.addParameter(param.substring(PREFIX_PARAMETER.length()), requestParams.getParameter(param));
      }
      else if (param.startsWith(PREFIX_SETTING))
      {
        queryOptions.addSetting(param.substring(PREFIX_SETTING.length()), requestParams.getStringParameter(param, ""));
      }*/
      
    

    if(parameters.isWrapItUp()) {
      String uuid = engine.wrapQuery(out, cdaSettings, queryOptions);
      logger.debug("doQuery: query wrapped as " + uuid);
      writeOut(out, uuid);
      return;
    }
    
    // we'll allow for the special "callback" param to be used, and passed as settingcallback to jsonp exports
    if (!parameters.getJsonCallback().equals("<blank>"))
    {
      queryOptions.addSetting(JSONP_CALLBACK, parameters.getJsonCallback());
    }
    Exporter exporter = ExporterEngine.getInstance().getExporter(queryOptions.getOutputType(), queryOptions.getExtraSettings());
    
    String attachmentName = exporter.getAttachmentName();
    String mimeType = (attachmentName == null) ? null : getMimeType(attachmentName);
    if(StringUtils.isEmpty(mimeType)){
      mimeType = exporter.getMimeType();
    }
    
    if (parameters != null);//XXX + FIXME ==  if (this.parameterProviders != null)  
    {
      setResponseHeaders(mimeType, attachmentName);
    }
    // Finally, pass the query to the engine
    engine.doQuery(out, cdaSettings, queryOptions);

  }

  //@Exposed(accessLevel = AccessLevel.PUBLIC)
  public void unwrapQuery(final OutputStream out,
          final String path, final String solution, final String file, final String uuid)  throws Exception
  {
    final CdaEngine engine = CdaEngine.getInstance();
    //final ICommonParameterProvider requestParams = requParam;
    final String relativePath = getRelativePath(path,solution,file);
    final CdaSettings cdaSettings = SettingsManager.getInstance().parseSettingsFile(relativePath);
    //String uuid = requestParams.getStringParameter("uuid", null);
    QueryOptions queryOptions = engine.unwrapQuery(uuid);
    if(queryOptions != null) {
      Exporter exporter = ExporterEngine.getInstance().getExporter(queryOptions.getOutputType(), queryOptions.getExtraSettings());
      
      String attachmentName = exporter.getAttachmentName();
      String mimeType = (attachmentName == null) ? null : getMimeType(attachmentName);
      if(StringUtils.isEmpty(mimeType)){
        mimeType = exporter.getMimeType();
      }
      
      if (relativePath!=null && uuid!= null);//XXX  ==  if (this.parameterProviders != null)  
      {
        setResponseHeaders(mimeType, attachmentName);
      }
      engine.doQuery(out, cdaSettings, queryOptions);
    }
    else {
      logger.error("unwrapQuery: uuid " + uuid + " not found.");
    }
    
  }

 // @Exposed(accessLevel = AccessLevel.PUBLIC)
  public void listQueries(final OutputStream out,
          final String path,final String solution,final String file, final String outputType) throws Exception
  {
    final CdaEngine engine = CdaEngine.getInstance();
    //final ICommonParameterProvider requestParams = requParam;
    final String relativePath = getRelativePath(path,solution,file);
    if(StringUtils.isEmpty(relativePath)){
      throw new IllegalArgumentException("No path provided");
    }
    IRepositoryAccess repAccess = CdaEngine.getEnvironment().getRepositoryAccess();
    logger.debug("Do Query: getRelativePath:" + relativePath);
    final CdaSettings cdaSettings = SettingsManager.getInstance().parseSettingsFile(relativePath);

    // Handle the query itself and its output format...
    final DiscoveryOptions discoveryOptions = new DiscoveryOptions();
    discoveryOptions.setOutputType(outputType);

    String mimeType = ExporterEngine.getInstance().getExporter(discoveryOptions.getOutputType()).getMimeType();
    setResponseHeaders(mimeType);
    engine.listQueries(out, cdaSettings, discoveryOptions);
  }

 // @Exposed(accessLevel = AccessLevel.PUBLIC)
  public void listParameters(final OutputStream out, 
          final String path, final String solution,final String file, final String outputType,final String dataAccessId) throws Exception
  {
    final CdaEngine engine = CdaEngine.getInstance();
   // final ICommonParameterProvider requestParams = requParam;
    final String relativePath = getRelativePath(path,solution,file);
    IRepositoryAccess repAccess = CdaEngine.getEnvironment().getRepositoryAccess();
    logger.debug("Do Query: getRelativePath:" + relativePath);
    final CdaSettings cdaSettings = SettingsManager.getInstance().parseSettingsFile(relativePath);

    // Handle the query itself and its output format...
    final DiscoveryOptions discoveryOptions = new DiscoveryOptions();
    discoveryOptions.setOutputType(outputType);
    discoveryOptions.setDataAccessId(dataAccessId);

    String mimeType = ExporterEngine.getInstance().getExporter(discoveryOptions.getOutputType()).getMimeType();
    setResponseHeaders(mimeType);

    engine.listParameters(out, cdaSettings, discoveryOptions);
  }


  //@Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.XML)
  public void getCdaFile(final OutputStream out,final String path, IResponseTypeHandler response) throws Exception
  {
    String document = getResourceAsString(StringUtils.replace(path, "///", "/"), FileAccess.READ,response);// ISolutionRepository.ACTION_UPDATE);//TODO:check
    writeOut(out, document);
  }

 // @Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.PLAIN_TEXT)
  public boolean writeCdaFile(final OutputStream out,final String path,final String solution, final String file, final String data ) throws Exception
  {
    //TODO: Validate the filename in some way, shape or form!
    IRepositoryAccess repository = CdaEngine.getEnvironment().getRepositoryAccess();
    //final ICommonParameterProvider requestParams = requParam;
    // Check if the file exists and we have permissions to write to it
    String relativePath = getRelativePath(path,solution,file);

    if (repository.canWrite(relativePath)&&data!=null)
    { 
      // TODO: this is really to write anything here
      switch(repository.publishFile(relativePath, data.getBytes(ENCODING), true)){
        case OK:
          SettingsManager.getInstance().clearCache();
          writeOut(out, "File saved.");
          return true;
        case FAIL:
          writeOut(out, "Save unsuccessful!");
          logger.error("writeCdaFile: saving " + relativePath);
          break;
      }
    }
    else
    {
      throw new AccessDeniedException(relativePath, null);
    }
    return false;
  }
  
  public boolean deleteCdaFile(final String path,final String solution, final String file) throws Exception
  {
    //TODO: Validate the filename in some way, shape or form!
    IRepositoryAccess repository = CdaEngine.getEnvironment().getRepositoryAccess();
    //final ICommonParameterProvider requestParams = requParam;
    // Check if the file exists and we have permissions to write to it
    String relativePath = getRelativePath(path,solution,file);

    if (repository.canWrite(relativePath))
    { 
      return repository.removeFileIfExists(relativePath);
    }
    else
    {
      throw new AccessDeniedException(relativePath, null);
    }
  }

 // @Exposed(accessLevel = AccessLevel.PUBLIC)
  public void getCdaList(final OutputStream out,final String outputType) throws Exception
  {
    final CdaEngine engine = CdaEngine.getInstance();

    final DiscoveryOptions discoveryOptions = new DiscoveryOptions();
    discoveryOptions.setOutputType(outputType);
	ISessionUtils sessionUtils = CdaEngine.getEnvironment().getSessionUtils();
    String mimeType = ExporterEngine.getInstance().getExporter(discoveryOptions.getOutputType()).getMimeType();
    setResponseHeaders(mimeType);
    engine.getCdaList(out, discoveryOptions, sessionUtils.getCurrentSession());
  }

 // @Exposed(accessLevel = AccessLevel.ADMIN, outputType = MimeType.PLAIN_TEXT)
  public void clearCache(final OutputStream out) throws Exception
  {
    SettingsManager.getInstance().clearCache();
    AbstractDataAccess.clearCache();

    out.write("Cache cleared".getBytes());
  }
  
 // @Exposed(accessLevel = AccessLevel.ADMIN)
  public void cacheMonitor(final OutputStream out,String method,ICommonParameterProvider requParam) throws Exception{
              
    CacheMonitorHandler.getInstance().handleCall(method,requParam, out);
  }


  public void syncronize(final ICommonParameterProvider pathParams, final OutputStream out) throws Exception
  {
    throw new UnsupportedOperationException("Feature not implemented yet");
//    final SyncronizeCdfStructure syncCdfStructure = new SyncronizeCdfStructure();
//    syncCdfStructure.syncronize(userSession, out, pathParams);
  }


 // @Override
  public Log getLogger()
  {
    return logger;
  }


  private String getRelativePath(final String originalPath, final String solution,final String file) throws UnsupportedEncodingException
  {
	String joined = "";
	joined += (StringUtils.isEmpty(solution) ? "" : URLDecoder.decode(solution, ENCODING) + "/");
	joined += (StringUtils.isEmpty(originalPath) ? "" : URLDecoder.decode(originalPath, ENCODING));
	joined += (StringUtils.isEmpty(file) ? "" : "/" + URLDecoder.decode(file, ENCODING));
	joined = joined.replaceAll("//", "/");
	return joined;
  }


  public String getResourceAsString(final String path, final HashMap<String, String> tokens) throws IOException
  {
    // Read file
    IRepositoryAccess repository = CdaEngine.getEnvironment().getRepositoryAccess();
    String resourceContents = StringUtils.EMPTY;
    
    if (repository.resourceExists(path))
    {
      InputStream in = null;
      try
      {
        in = repository.getResourceInputStream(path, FileAccess.READ);
        resourceContents = IOUtils.toString(in);
      }
      finally 
      {
        IOUtils.closeQuietly(in);
      }
    }
    
    // Make replacement of tokens
    if (tokens != null)
    {
      for (final String key : tokens.keySet())
      { 
        resourceContents = StringUtils.replace(resourceContents, key, tokens.get(key));
      }
    }
    return resourceContents;
  }

  
  public String getResourceAsString(final String path, FileAccess access,IResponseTypeHandler response) throws IOException, AccessDeniedException{
    IRepositoryAccess repository = CdaEngine.getEnvironment().getRepositoryAccess();
    if(repository.hasAccess(path, access)){
      HashMap<String, String> keys = new HashMap<String, String>();
      //Locale locale = LocaleHelper.getLocale();
      Locale locale = response.getLocale();
      
      if (logger.isDebugEnabled())
      {
        logger.debug("Current user locale: " + locale.toString());
      }
      keys.put("#{LANGUAGE_CODE}", locale.toString());
      return getResourceAsString(path, keys);
    }
    else
    {
      throw new AccessDeniedException(path, null);
    }
  }

  //@Exposed(accessLevel = AccessLevel.PUBLIC)
  // TODO: this has to go into cda-pentaho or needs to be refactored
  @Deprecated
  public void editFile(final OutputStream out, final String path,final String solution, final String file,IResponseTypeHandler response) throws Exception 
  {
    IRepositoryAccess repository = CdaEngine.getEnvironment().getRepositoryAccess();
    
    // Check if the file exists and we have permissions to write to it
    String relativePath = getRelativePath(path,solution,file);
    if (repository.canWrite(relativePath))
    {
      boolean hasCde = repository.resourceExists("system/pentaho-cdf-dd");
      
      final String editorPath = "system/" + PLUGIN_NAME + (hasCde? EXT_EDITOR_SOURCE : EDITOR_SOURCE);
      writeOut(out, getResourceAsString(editorPath,FileAccess.EXECUTE,response));
    }
    else
    {
      setResponseHeaders("text/plain");
      out.write("Access Denied".getBytes());
    }


  }
  
  // TODO: this has to go into cda-pentaho or needs to be refactored
  @Deprecated
  //@Exposed(accessLevel = AccessLevel.PUBLIC)
  public void previewQuery(final OutputStream out,IResponseTypeHandler response) throws Exception
  {
    final String previewerPath = "system/" + PLUGIN_NAME + PREVIEWER_SOURCE;
    writeOut(out, getResourceAsString(previewerPath, FileAccess.EXECUTE,response));
  }


  //@Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.CSS)
  public void getCssResource(final OutputStream out, final String resource) throws Exception
  {
    getResource(out, resource);
  }

  //@Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.JAVASCRIPT)
  public void getJsResource(final OutputStream out, final String resource) throws Exception
  {
    getResource(out, resource);
  }

  public void getResource(final OutputStream out, String resource) throws Exception
  {
    //String resource = requParam.getStringParameter("resource", null);
    resource = resource.startsWith("/") ? resource : "/" + resource;
    IRepositoryAccess repAccess =CdaEngine.getEnvironment().getRepositoryAccess();
    IRepositoryFile resFile = repAccess.getRepositoryFile(resource, FileAccess.READ);
    if (resFile != null && resFile.exists()) {
    	out.write(resFile.getData());
  	}
  }

/*
  private void getResource(final OutputStream out, final String resource) throws IOException
  {
    IRepositoryAccess repAccess = CdaEngine.getEnvironment().getRepositoryAccess();
    final String path = repAccess.getSolutionPath("system/" + PLUGIN_NAME + resource);//PentahoSystem.getApplicationContext().getSolutionPath("system/" + PLUGIN_NAME + resource); //$NON-NLS-1$ //$NON-NLS-2$

    final File file = new File(path);
    final InputStream in = new FileInputStream(file);
    
    try{
      IOUtils.copy(in, out);
    }
    finally {
      IOUtils.closeQuietly(in);
    }
    
  }*/
  
  //@Exposed(accessLevel = AccessLevel.PUBLIC, outputType = MimeType.JSON)
  public void listDataAccessTypes(final OutputStream out,final boolean refreshCache) throws Exception
  {
    //boolean refreshCache = Boolean.parseBoolean(requParam.getStringParameter("refreshCache", "false"));
    
    DataAccessConnectionDescriptor[] data = SettingsManager.getInstance().
            getDataAccessDescriptors(refreshCache);

    StringBuilder output = new StringBuilder("");
    if (data != null)
    {
      output.append("{\n");
      for (DataAccessConnectionDescriptor datum : data)
      {
                output.append(datum.toJSON()).append(",\n");
      }
      writeOut(out, output.toString().replaceAll(",\n\\z", "\n}"));
    }
  }

  //@Exposed(accessLevel = AccessLevel.PUBLIC)
  public void cacheController(OutputStream out, String method, String obj)
  {
	  if (CdaEngine.getEnvironment().supportsCacheScheduler()) {
		  ICacheScheduleManager manager = CdaEngine.getEnvironment().getCacheScheduler();
		  manager.handleCall(method, obj, out);
	  }
  }

  //@Exposed(accessLevel = AccessLevel.ADMIN)
  public void manageCache(final OutputStream out,IResponseTypeHandler response) throws Exception
  {
    writeOut(out, getResourceAsString(CACHE_MANAGER_PATH, FileAccess.EXECUTE,response));
  }


  public String getPluginName() {
    return "cda";
  }
  
  
  private void writeOut(OutputStream out,String uuid){
      try{
      out.write(uuid.getBytes());
      }catch(IOException e){
          logger.error("Failed to write to stream");//XXX needs checking, wich error to log
      }
  }
  private String getMimeType(String attachmentName){
      return MimeTypes.getMimeType(attachmentName);
  }
  private void setResponseHeaders(String mimeType, String attachmentName){
       setResponseHeaders(mimeType, 0, attachmentName);
  }

  private void setResponseHeaders(String mimeType){
      setResponseHeaders(mimeType, 0, null);
  }
  private void setResponseHeaders(final String mimeType, final int cacheDuration, final String attachmentName)
    {
        if (responseHandler.hasResponse())
            responseHandler.setResponseHeaders(mimeType, cacheDuration, attachmentName);
//        else logger.warn("Parameter 'httpresponse' not found!");
    }
  
  
}
