/********************************************************************************/
/*										*/
/*		BseanError.java 						*/
/*										*/
/*	Handle error contents from FAIT 					*/
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



package edu.brown.cs.faitbb.bsean;

import edu.brown.cs.bubbles.bale.BaleConstants;
import edu.brown.cs.bubbles.bale.BaleFactory;

import org.w3c.dom.Element;

import edu.brown.cs.ivy.xml.IvyXml;

import javax.swing.text.Position;
import javax.swing.text.BadLocationException;
import java.io.File;
import java.util.Objects;

class BseanError implements BseanConstants, BaleConstants {


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private ErrorLevel error_level;
private File	file_name;
private String	class_name;
private String	error_id;
private String	method_name;
private String	method_signature;
private String	method_id;
private int	start_offset;
private int	end_offset;
private int	line_number;
private Position error_position;
private String	error_message;
private String	error_subtype;
private String	error_checktype;
private BaleFileOverview for_document;





/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

BseanError(Element outxml,Element errxml,Element where)
{
   error_level = IvyXml.getAttrEnum(errxml,"LEVEL",ErrorLevel.NOTE);
   file_name = new File(IvyXml.getTextElement(outxml,"FILE"));
   class_name = IvyXml.getAttrString(outxml,"CLASS");
   method_name = IvyXml.getAttrString(outxml,"METHOD");
   method_signature = IvyXml.getAttrString(outxml,"SIGNATURE");
   method_id = IvyXml.getAttrString(outxml,"HASHCODE");
   start_offset = IvyXml.getAttrInt(where,"START");
   end_offset = IvyXml.getAttrInt(where,"END");
   line_number = IvyXml.getAttrInt(where,"LINE");
   error_message = IvyXml.getTextElement(errxml,"MESSAGE");
   error_subtype = IvyXml.getTextElement(errxml,"SUBTYPE");
   error_checktype = IvyXml.getTextElement(errxml,"CHECKTYPE");
   error_id = IvyXml.getTextElement(errxml,"HASHCODE");

   for_document = BaleFactory.getFactory().getFileOverview(null,file_name);
   start_offset = for_document.mapOffsetToJava(start_offset);
   end_offset = for_document.mapOffsetToJava(end_offset);

   if (start_offset < 0 && line_number > 0 && for_document != null) {
      start_offset = for_document.findLineOffset(line_number);
      end_offset = for_document.findLineOffset(line_number+1);
    }

   error_position = null;
   try {
      if (for_document != null && start_offset > 0) {
	 error_position = for_document.createPosition(start_offset);
       }
    }
   catch (BadLocationException e) { }
}




/********************************************************************************/
/*										*/
/*	Access methods								*/
/*										*/
/********************************************************************************/

File getFile()					{ return file_name; }

int getStartOffset()				{ return start_offset; }
int getEclipseOffset()
{
   return for_document.mapOffsetToEclipse(start_offset);
}


ErrorLevel getErrorLevel()			{ return error_level; }

String getErrorSubtype()			{ return error_subtype; }

String getErrorCheckType()			{ return error_checktype; }

int getLine()					{ return line_number; }

int getDocumentOffset()
{
   if (error_position == null) return -1;

   return error_position.getOffset();
}

String getMessage()				{ return error_message; }

String getId()					{ return error_id; }

String getMethod() {
   return class_name + "." + method_name + method_signature + "@" + method_id;
}

String getLineKey()
{
   return file_name.getPath() + ":" + start_offset;
}

String getFullKey()
{
   String k = file_name.getPath() + ":" + start_offset + ":" + end_offset;
   k += ":" + error_message.hashCode() + ":" + error_level;
   if (error_subtype != null) k += ":" + error_subtype;
   if (error_checktype != null) k += ":" + error_checktype;
   return k;
}



/********************************************************************************/
/*										*/
/*	Check for equivalence							*/
/*										*/
/********************************************************************************/

boolean sameError(BseanError er)
{
   if (!Objects.equals(error_message,er.error_message)) return false;
   if (!Objects.equals(file_name,er.file_name)) return false;
   if (start_offset != er.start_offset) return false;
   if (end_offset != er.end_offset) return false;
   if (error_level != er.error_level) return false;
   if (!Objects.equals(error_subtype,er.error_subtype)) return false;
   if (!Objects.equals(error_checktype,er.error_checktype)) return false;
   return true;
}




/********************************************************************************/
/*										*/
/*	Debugging methods							*/
/*										*/
/********************************************************************************/

@Override public String toString()
{
   StringBuffer buf = new StringBuffer();
   buf.append(error_level);
   buf.append("@");
   buf.append(file_name);
   buf.append(":");
   buf.append(line_number);
   buf.append("(");
   buf.append(start_offset);
   buf.append("-");
   buf.append(end_offset);
   buf.append(")");
   buf.append(class_name);
   buf.append(".");
   buf.append(method_name);
   buf.append(method_signature);
   buf.append(method_id);
   buf.append(";");
   buf.append(error_message);
   buf.append("*");
   if (error_subtype != null) buf.append(error_subtype);
   if (error_checktype != null) buf.append(error_checktype);

   return buf.toString();
}



}	// end of class BseanError




/* end of BseanError.java */

