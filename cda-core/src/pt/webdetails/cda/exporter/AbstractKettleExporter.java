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

package pt.webdetails.cda.exporter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.swing.table.TableModel;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.util.StringUtil;

import pt.webdetails.cda.CdaBoot;
import plugins.org.pentaho.di.robochef.kettle.DynamicTransConfig;
import plugins.org.pentaho.di.robochef.kettle.DynamicTransMetaConfig;
import plugins.org.pentaho.di.robochef.kettle.DynamicTransformation;
import pt.webdetails.cda.utils.kettle.RowMetaToTableModel;
import plugins.org.pentaho.di.robochef.kettle.RowProductionManager;
import plugins.org.pentaho.di.robochef.kettle.TableModelInput;

/**
 * Generic Kettle class to handle exports
 * User: pedro
 * Date: Mar 12, 2010
 * Time: 3:01:27 PM
 */
public abstract class AbstractKettleExporter extends AbstractExporter implements Exporter, RowProductionManager
{

  private static final Log logger = LogFactory.getLog(AbstractKettleExporter.class);
  
  public static final String COLUMN_HEADERS_SETTING = "columnHeaders";
  public static final String FILE_EXTENSION_SETTING = "fileExtension";

  protected ExecutorService executorService = Executors.newCachedThreadPool();
  protected Collection<Callable<Boolean>> inputCallables = new ArrayList<Callable<Boolean>>();
  
  
  private SimpleDateFormat dateFormat = new SimpleDateFormat("yyMMddHHmmssZ");
  private String filename;
  
  private static long DEFAULT_ROW_PRODUCTION_TIMEOUT = 120;
  private static TimeUnit DEFAULT_ROW_PRODUCTION_TIMEOUT_UNIT = TimeUnit.SECONDS;

  
  protected AbstractKettleExporter(Map<String, String> extraSettings)
  {
    super(extraSettings);
  }
  
  
  protected abstract String getExportStepDefinition(String name);

  
  protected abstract String getType();
  

  public void startRowProduction()
  {
    String timeoutStr = CdaBoot.getInstance().getGlobalConfig().getConfigProperty("pt.webdetails.cda.DefaultRowProductionTimeout");
    long timeout = StringUtil.isEmpty(timeoutStr)? DEFAULT_ROW_PRODUCTION_TIMEOUT : Long.parseLong(timeoutStr);
    String unitStr = CdaBoot.getInstance().getGlobalConfig().getConfigProperty("pt.webdetails.cda.DefaultRowProductionTimeoutTimeUnit");
    TimeUnit unit = StringUtil.isEmpty(unitStr)? DEFAULT_ROW_PRODUCTION_TIMEOUT_UNIT : TimeUnit.valueOf(unitStr);
    startRowProduction(timeout, unit);
  }


  public void startRowProduction(long timeout, TimeUnit unit)
  {
    try
    {
      List<Future<Boolean>> results = executorService.invokeAll(inputCallables, timeout, unit);
      for (Future<Boolean> result : results)
      {
        result.get();
      }
    }
    catch (InterruptedException e)
    {
      logger.error(e);
    }
    catch (ExecutionException e)
    {
      logger.error(e);
    }
  }


  public void export(final OutputStream out, final TableModel tableModel) throws ExporterException
  {
    TableModel output = null;
    inputCallables.clear();

    try
    {

      final DynamicTransMetaConfig transMetaConfig = new DynamicTransMetaConfig(DynamicTransMetaConfig.Type.EMPTY, "Exporter", null, null);
      final DynamicTransConfig transConfig = new DynamicTransConfig();

      transConfig.addConfigEntry(DynamicTransConfig.EntryType.STEP, "input", "<step><name>input</name><type>Injector</type></step>");
      transConfig.addConfigEntry(DynamicTransConfig.EntryType.STEP, "export", getExportStepDefinition("export"));

      transConfig.addConfigEntry(DynamicTransConfig.EntryType.HOP, "input", "export");

      TableModelInput input = new TableModelInput();
      transConfig.addInput("input", input);
      inputCallables.add(input.getCallableRowProducer(tableModel, true));


      RowMetaToTableModel outputListener = new RowMetaToTableModel(false, true, false);
      transConfig.addOutput("export", outputListener);

      DynamicTransformation trans = new DynamicTransformation(transConfig, transMetaConfig);
      trans.executeCheckedSuccess(null, null, this);
      logger.info(trans.getReadWriteThroughput());

      // Transformation executed ok, let's return the file
      copyFileToOutputStream(out);

      output = outputListener.getRowsWritten();
      if(output != null){
        logger.debug(output.getRowCount() + " rows written.");
      }
    }
    catch (KettleException e)
    {
      throw new ExporterException("Kettle exception during " + getType() + " query ", e);
    }
    catch (Exception e)
    {
      throw new ExporterException("Unknown exception during " + getType() + " query ", e);
    }


  }


  protected String getFileName()
  {
    filename = "pentaho-cda-" + getType() + "-" + dateFormat.format(Calendar.getInstance().getTime()) + "-" + UUID.randomUUID().toString();
    return filename;
  }

  
  private void copyFileToOutputStream(OutputStream os) throws IOException
  {
    
    File file = new File(System.getProperty("java.io.tmpdir") + File.separator + filename + "." + getType());
    FileInputStream is = new FileInputStream(file);

    try{
      IOUtils.copy(is, os);
    } 
    finally {
      IOUtils.closeQuietly(is);
    }

    // temp file not needed anymore - delete it
    file.delete();

  }

}
