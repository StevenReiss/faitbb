/********************************************************************************/
/*										*/
/*		BseanConstants.java						*/
/*										*/
/*	Bubbles SEmantic Analysis external constants				*/
/*										*/
/********************************************************************************/
/*	Copyright 2011 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2011, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 * This program and the accompanying materials are made available under the	 *
 * terms of the Eclipse Public License v1.0 which accompanies this distribution, *
 * and is available at								 *
 *	http://www.eclipse.org/legal/epl-v10.html				 *
 *										 *
 ********************************************************************************/

/* SVN: $Id$ */



package edu.brown.cs.faitbb.bsean;

import java.util.EventListener;

public interface BseanConstants
{


/********************************************************************************/
/*										*/
/*	Error constants 							*/
/*										*/
/********************************************************************************/

enum ErrorLevel {
   NOTE, WARNING, ERROR
}



/********************************************************************************/
/*										*/
/*	Color Names								*/
/*										*/
/********************************************************************************/

String PROBLEM_TOP_COLOR_PROP = "Bsean.ProblemTopColor";
String PROBLEM_BOTTOM_COLOR_PROP = "Bsean.ProblemBottomColor";
String PROBLEM_OVERVIEW_COLOR_PROP = "Bsean.ProblemOverviewColor";
String PROBLEM_ERROR_COLOR_PROP = "Bsean.ProblemErrorColor";
String PROBLEM_WARNING_COLOR_PROP = "Bsean.ProblemWarningColor";
String PROBLEM_NOTICE_COLOR_PROP = "Bsean.ProblemNoticeColor";

String EXPLAIN_TOP_COLOR_PROP = "Bsean.ExplainTopColor";
String EXPLAIN_BOTTOM_COLOR_PROP = "Bsean.ExplainBottomColor";
String EXPLAIN_ANNOT_COLOR_PROP = "Bsean.ExplainAnnotColor";

String VAR_TOP_COLOR_PROP = "Bsean.VarTopColor";
String VAR_BOTTOM_COLOR_PROP = "Bsean.VarBottomColor";



/********************************************************************************/
/*										*/
/*	Property names								*/
/*										*/
/********************************************************************************/

String PROBLEM_WIDTH="Bsean.problem.width";
String PROBLEM_HEIGHT="Bsean.problem.height";

String VAR_WIDTH_PROP = "Bsean.var.width";
String VAR_HEIGHT_PROP = "Bsean.var.height";



/********************************************************************************/
/*										*/
/*	Update callbacks							*/
/*										*/
/********************************************************************************/

interface BseanErrorHandler extends EventListener {

   void handleErrorsUpdated();

}


}	// end of interface BseanConstants




/* end of BseanConstants.java */

