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

package pt.webdetails.cda.utils;

import java.util.ArrayList;
import java.util.List;

import javax.swing.table.TableModel;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.reporting.engine.classic.core.util.TypedTableModel;

import pt.webdetails.cda.CdaEngine;
import pt.webdetails.cpf.repository.IRepositoryAccess.FileAccess;
import pt.webdetails.cpf.repository.IRepositoryAccess;
import pt.webdetails.cpf.repository.IRepositoryFile;
import pt.webdetails.cpf.session.IUserSession;
import edu.emory.mathcs.backport.java.util.Arrays;

/**
 * Utility class for SolutionRepository utils
 * User: pedro
 * Date: Feb 16, 2010
 * Time: 6:13:33 PM
 */
public class SolutionRepositoryUtils
{


  private static final Log logger = LogFactory.getLog(SolutionRepositoryUtils.class);

  private static SolutionRepositoryUtils _instance;

  public SolutionRepositoryUtils()
  {
  }


  public static synchronized SolutionRepositoryUtils getInstance()
  {

    if (_instance == null)
    {
      _instance = new SolutionRepositoryUtils();
    }

    return _instance;
  }


  public TableModel getCdaList(final IUserSession userSession)
  {

    logger.debug("Getting CDA list");
    IRepositoryAccess repository = CdaEngine.getEnvironment().getRepositoryAccess();
	IRepositoryFile[] cdaTree = repository.getPluginFiles("/",FileAccess.READ);
    @SuppressWarnings("unchecked")

    List<IRepositoryFile> cdaFiles = new ArrayList<IRepositoryFile>(Arrays.asList(cdaTree));
       

    final int rowCount = cdaFiles.size();

    // Define names and types
    final String[] colNames = {"name", "path"};
    final Class<?>[] colTypes = {String.class, String.class};
    final TypedTableModel typedTableModel = new TypedTableModel(colNames, colTypes, rowCount);

    for (IRepositoryFile file : cdaFiles)
    {
      typedTableModel.addRow(new Object[]{file.getFileName(), file.getFullPath()});

    }

    return typedTableModel;

  }
}
