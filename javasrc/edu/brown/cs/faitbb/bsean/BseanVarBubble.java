/********************************************************************************/
/*										*/
/*		BseanVarBubble.java						*/
/*										*/
/*	Bubble containing a variable description				*/
/*										*/
/********************************************************************************/
/*	Copyright 2013 Brown University -- Steven P. Reiss		      */
/*********************************************************************************
 *  Copyright 2013, Brown University, Providence, RI.				 *
 *										 *
 *			  All Rights Reserved					 *
 *										 *
 *  Permission to use, copy, modify, and distribute this software and its	 *
 *  documentation for any purpose other than its incorporation into a		 *
 *  commercial product is hereby granted without fee, provided that the 	 *
 *  above copyright notice appear in all copies and that both that		 *
 *  copyright notice and this permission notice appear in supporting		 *
 *  documentation, and that the name of Brown University not be used in 	 *
 *  advertising or publicity pertaining to distribution of the software 	 *
 *  without specific, written prior permission. 				 *
 *										 *
 *  BROWN UNIVERSITY DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS		 *
 *  SOFTWARE, INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND		 *
 *  FITNESS FOR ANY PARTICULAR PURPOSE.  IN NO EVENT SHALL BROWN UNIVERSITY	 *
 *  BE LIABLE FOR ANY SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY 	 *
 *  DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS,		 *
 *  WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS		 *
 *  ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE 	 *
 *  OF THIS SOFTWARE.								 *
 *										 *
 ********************************************************************************/



package edu.brown.cs.faitbb.bsean;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.w3c.dom.Element;

import edu.brown.cs.ivy.mint.MintConstants.CommandArgs;
import edu.brown.cs.ivy.swing.SwingGridPanel;
import edu.brown.cs.ivy.swing.SwingListSet;
import edu.brown.cs.ivy.swing.SwingText;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;
import edu.brown.cs.bubbles.bale.BaleConstants.BaleContextConfig;
import edu.brown.cs.bubbles.board.BoardColors;
import edu.brown.cs.bubbles.board.BoardProperties;
import edu.brown.cs.bubbles.board.BoardThreadPool;
import edu.brown.cs.bubbles.buda.BudaBubble;


@SuppressWarnings("unused")
class BseanVarBubble extends BudaBubble implements BseanConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private transient Map<VarType,Map<Integer,VarEntity>> entity_map;
private transient BaleContextConfig bale_context;
private transient List<VarValue> other_values;
private transient VarLocation at_location;
private transient List<EntityBox> entity_boxes;
private transient EntitySelector selection_listener;
private transient Map<String,Set<String>> ignore_values;

private static final long serialVersionUID = 1;




/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

BseanVarBubble(BaleContextConfig cfg,Element vset) throws BseanException
{
   bale_context = cfg;
   entity_map = new HashMap<>();
   other_values = new ArrayList<>();
   at_location = null;
   selection_listener = new EntitySelector();

   loadQueryResult(vset);

   if (entity_map.isEmpty()) throw new BseanException("No values");

   JPanel pnl = setupPanel();

   JScrollPane jsp = new JScrollPane(pnl);
   Dimension sz = pnl.getPreferredSize();
   BoardProperties bp = BoardProperties.getProperties("Bsean");
   int wd = bp.getInt(VAR_WIDTH_PROP,600);
   int ht = bp.getInt(VAR_HEIGHT_PROP,600);
   wd = Math.min(wd,sz.width);
   ht = Math.min(ht,sz.height);
   jsp.setSize(wd,ht);

   setContentPane(jsp);
}



/********************************************************************************/
/*										*/
/*	Load information from query result					*/
/*										*/
/********************************************************************************/

private void loadQueryResult(Element vset)
{
   for (Element refval : IvyXml.children(vset,"REFVALUE")) {
      if (at_location == null) {
	 Element xloc = IvyXml.getChild(refval,"LOCATION");
	 at_location = new VarLocation(xloc);
	 Element ploc = IvyXml.getChild(xloc,"POINT");
       }
      String cid = IvyXml.getAttrString(refval,"CALL") + "@" + IvyXml.getAttrString(refval,"CALLID");
      Element relt = IvyXml.getChild(refval,"REFERENCE");
      VarRef varref = new VarRef(relt,cid);
      VarValue varval = new VarValue(IvyXml.getChild(refval,"VALUE"));
      VarType vartyp = varval.getType();
      List<VarEntity> entset = varval.getEntities();
      if (entset != null) {
	 Map<Integer,VarEntity> emap = entity_map.get(vartyp);
	 if (emap == null) {
	    emap = new TreeMap<>();
	    entity_map.put(vartyp,emap);
	  }
	 for (VarEntity vent : entset) {
	    VarEntity ovent = emap.putIfAbsent(vent.getId(),vent);
	    if (ovent != null) vent = ovent;
	    vent.addReference(varref);
	  }
       }
      else {
	 other_values.add(varval);
       }
    }

   ignore_values = new HashMap<>();
   for (Element stval : IvyXml.children(vset,"SUBTYPE")) {
      String nm = IvyXml.getAttrString(stval,"NAME");
      String vals = IvyXml.getAttrString(stval,"DEFAULTS");
      if (vals == null) continue;
      Set<String> rslt = new HashSet<>();
      StringTokenizer tok = new StringTokenizer(vals);
      while (tok.hasMoreTokens()) {
	 String v = tok.nextToken();
	 rslt.add(v);
       }
      ignore_values.put(nm,rslt);
    }
}



/********************************************************************************/
/*										*/
/*	Methods to setup display panel						*/
/*										*/
/********************************************************************************/

private JPanel setupPanel()
{
   SwingGridPanel pnl = new VarPanel();
   int y = 0;

   // JLabel ttl = new JLabel("Flow Analysis for " + at_location.getVariableName());
   JLabel ttl = new JLabel("Flow Analysis for " + bale_context.getToken());
   ttl.setFont(SwingText.deriveLarger(ttl.getFont()));
   pnl.addGBComponent(ttl,0,y++,0,1,10,0);

   // location
   JLabel lbl1 = new JLabel("At:");
   String loc = at_location.getLineNumber() + " in " + at_location.getMethodName();
   lbl1.setOpaque(false);
   JLabel lbl2 = new JLabel(loc);
   lbl2.setOpaque(false);
   pnl.addGBComponent(lbl1,0,y,1,1,0,0);
   pnl.addGBComponent(lbl2,1,y++,0,1,10,0);

   entity_boxes = new ArrayList<>();

   for (Map.Entry<VarType,Map<Integer,VarEntity>> ent : entity_map.entrySet()) {
      pnl.addGBComponent(new JSeparator(),0,y++,0,1,10,0);
      VarType vt = ent.getKey();
      JLabel lbl3 = new JLabel("Type:  " + vt.getShortName());
      pnl.addGBComponent(lbl3,0,y++,0,1,10,0);
      for (Map.Entry<String,String> aent : vt.getAttributes().entrySet()) {
	 JLabel lbl4 = new JLabel(aent.getKey() + ":   " + aent.getValue());
	 pnl.addGBComponent(lbl4,1,y++,0,1,10,0);
       }
      JLabel lbl5 = new JLabel("Entities:");
      pnl.addGBComponent(lbl5,0,y++,1,1,0,0);
      Collection<VarEntity> entmap = ent.getValue().values();
      SwingListSet<VarEntity> lset = new SwingListSet<>(entmap);
      EntityBox ebox = new EntityBox(lset);
      entity_boxes.add(ebox);
      pnl.addGBComponent(new JScrollPane(ebox),1,y++,0,1,10,0);
    }
   for (VarValue vvar : other_values) {
      pnl.addGBComponent(new JSeparator(),0,y++,0,1,10,0);
      JLabel lbl6 = new JLabel("Type: " + vvar.getType().getShortName());
      pnl.addGBComponent(lbl6,0,y++,0,1,10,0);
      String details = vvar.getDetails();
      if (details != null) {
	 JLabel lbl7 = new JLabel(details);
	 pnl.addGBComponent(lbl7,1,y++,0,1,10,0);
       }
    }

   return pnl;
}



private static class VarPanel extends SwingGridPanel {

   private static final long serialVersionUID = 1;

   VarPanel() {
      setOpaque(false);
      BoardColors.setColors(this,VAR_TOP_COLOR_PROP);
    }

   @Override protected void paintComponent(Graphics g) {
      Color tc = BoardColors.getColor(VAR_TOP_COLOR_PROP);
      Color bc = BoardColors.getColor(VAR_BOTTOM_COLOR_PROP);
      if (tc.getRGB() != bc.getRGB()) {
	 Graphics2D g2 = (Graphics2D) g.create();
	 Dimension sz = getSize();
	 Paint p = new GradientPaint(0f,0f,tc,0f,sz.height,bc);
	 Shape r = new Rectangle2D.Float(0,0,sz.width,sz.height);
	 g2.setPaint(p);
	 g2.fill(r);
       }
      try {
	 super.paintComponent(g);
       }
      catch (Throwable t) { }
    }

}	// end of inner class VarPanel


private class EntityBox extends JList<VarEntity> {

   private static final long serialVersionUID = 1;


   EntityBox(SwingListSet<VarEntity> eset) {
      super(eset);
      int ct = eset.getSize();
      setName("Entities");
      setVisibleRowCount(Math.max(10,ct));
      setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      setOpaque(false);
      setBackground(BoardColors.transparent());
      setToolTipText("Entity selection area");
      addListSelectionListener(selection_listener);
    }

   @Override public boolean getScrollableTracksViewportWidth()	{ return true; }

   @Override public String getToolTipText(MouseEvent evt) {
      int idx = locationToIndex(evt.getPoint());
      ListModel<VarEntity> mdl = getModel();
      if (idx < 0 || idx >= mdl.getSize()) return null;
      VarEntity ve = mdl.getElementAt(idx);
      if (ve == null) return null;

      return ve.getToolTip();
    }


}	// end of inner vlass EntityBox


private final class EntitySelector implements ListSelectionListener {

   @Override public void valueChanged(ListSelectionEvent evt) {
      EntityBox ebox = (EntityBox) evt.getSource();
      for (EntityBox eb : entity_boxes) {
	 if (eb != ebox) {
	    eb.clearSelection();
	  }
       }
    }

}


/********************************************************************************/
/*										*/
/*	Popup menu handler							*/
/*										*/
/********************************************************************************/

@Override public void handlePopupMenu(MouseEvent evt)
{
   JPopupMenu menu = new JPopupMenu();

   EntityBox baseeb = null;
   VarEntity baseent = null;
   VarEntity selent = null;
   EntityBox seleb = null;

   for (EntityBox eb : entity_boxes) {
      if (eb.getSelectedValue() != null) {
	 seleb = eb;
	 selent = eb.getSelectedValue();
       }
      if (evt.getY() > eb.getY() + eb.getHeight()) continue;
      baseeb = eb;
      if (evt.getY() > eb.getY()) {
	 Point p1 = SwingUtilities.convertPoint((Component) evt.getSource(),evt.getPoint(),eb);
	 int idx = eb.locationToIndex(p1);
	 if (idx >= 0) baseent = eb.getModel().getElementAt(idx);
       }
      break;
    }

   if (selent != null) {
      baseeb = seleb;
      baseent = selent;
    }

   if (baseent != null) {
      menu.add(new FlowToAction(baseent));
      VarType basetype = baseent.getType();
      for (Map.Entry<String,String> ent : basetype.getAttributes().entrySet()) {
	 String val = ent.getValue();
	 String st = ent.getKey();
	 Set<String> igns = ignore_values.get(st);
	 if (igns.contains(val)) continue;
	 FlowExplainAction fea = new FlowExplainAction(getButtonName(baseent,st,val),baseent,st,val);
	 menu.add(fea);
       }
    }
   if (baseeb != null) {
      FlowFromAction ffa = new FlowFromAction(baseeb);
      ffa.setEnabled(false);
      menu.add(ffa);
    }

   menu.add(getFloatBubbleAction());

   menu.show(this,evt.getX(),evt.getY());
}


private static String getButtonName(VarEntity ve,String nm,String vl)
{
   if (nm.startsWith("Check")) nm = nm.substring(5);
   return "Explain why value " + nm + " is " + vl;
}





private class FlowToAction extends AbstractAction implements Runnable {

   private transient VarEntity base_entity;
   private static final long serialVersionUID = 1;

   FlowToAction(VarEntity ent) {
      super("Show flow to " + ent.getShortName());
      base_entity = ent;
    }

   @Override public void actionPerformed(ActionEvent evt) {
      BoardThreadPool.start(this);
    }

   @Override public void run() {
      for (VarRef vr : base_entity.getReferences()) {
         CommandArgs args = new CommandArgs("FILE",bale_context.getDocument().getFile().getPath(),
               "QTYPE","TO",
               "LINE",at_location.getLineNumber(),
               "START",at_location.getStartPosition(),
               "LOCATION",at_location.getStartNodeType(),
               "AFTER",at_location.getAfterStartPosition(),
               "AFTERLOCATION",at_location.getAfterNodeType(),
               "METHOD",vr.getCallMethod(),
               "ENTITY",base_entity.getId(),
               "TYPE",base_entity.getType().getName(),
               "VARIABLE",at_location.getVariableName());
         IvyXmlWriter xw = new IvyXmlWriter();
         vr.outputXml(xw);
         BseanFactory fac = BseanFactory.getFactory();
         Element rslt = fac.sendFaitMessage(null,"QUERY",args,xw.toString());
         if (rslt == null) return;
         String msg = "Flow of " + base_entity.getShortName();
         for (Element qelt : IvyXml.children(rslt,"QUERY")) {
            try {
               BudaBubble nbbl = new BseanExplainBubble(qelt,msg,false);
               SwingUtilities.invokeLater(new BseanFactory.CreateBubble(BseanVarBubble.this,nbbl));
             }
            catch (BseanException e) { }
          }
       }
    }

}	// end of inner class FlowToAction



private class FlowFromAction extends AbstractAction implements Runnable {

   private transient EntityBox base_entity;
   private static final long serialVersionUID = 1;

   FlowFromAction(EntityBox eb) {
      super("Show flow from this point");

      base_entity = eb;
    }

   @Override public void actionPerformed(ActionEvent evt) {
      BoardThreadPool.start(this);
    }

   @Override public void run() {
    }

}	// end of inner class FlowFromAction



private class FlowExplainAction extends AbstractAction implements Runnable {

   private transient VarEntity base_entity;
   private String subtype_name;
   private String subtype_value;
   private static final long serialVersionUID = 1;

   FlowExplainAction(String bnm,VarEntity ve,String nm,String vl) {
      super(bnm);
      base_entity = ve;
      subtype_name = nm;
      subtype_value = vl;
    }

   @Override public void actionPerformed(ActionEvent evt) {
      BoardThreadPool.start(this);
    }

   @Override public void run() {
      for (VarRef vr : base_entity.getReferences()) {
         CommandArgs args = new CommandArgs("FILE",bale_context.getDocument().getFile().getPath(),
               "QTYPE","EXPLAIN",
               "LINE",at_location.getLineNumber(),
               "START",at_location.getStartPosition(),
               "LOCATION",at_location.getStartNodeType(),
               "AFTER",at_location.getAfterStartPosition(),
               "AFTERLOCATION",at_location.getAfterNodeType(),
               "METHOD",vr.getCallMethod(),
               "ENTITY",base_entity.getId(),
               "TYPE",base_entity.getType().getName(),
               "SBUTYPE",subtype_name,
               "SUBTYPEVLAUE",subtype_value,
               "VARIABLE",at_location.getVariableName());
         IvyXmlWriter xw = new IvyXmlWriter();
         vr.outputXml(xw);
         BseanFactory fac = BseanFactory.getFactory();
         Element rslt = fac.sendFaitMessage(null,"QUERY",args,xw.toString());
         if (rslt == null) return;
         Element rset = IvyXml.getChild(rslt,"RESULTSET");
         for (Element qelt : IvyXml.children(rset,"QUERY")) {
            try {
               BudaBubble nbbl = new BseanExplainBubble(qelt,null,false);
               SwingUtilities.invokeLater(new BseanFactory.CreateBubble(BseanVarBubble.this,nbbl));
             }
            catch (BseanException e) { }
          }
       }
    }

}	// end of inner class FlowExplainAction





/********************************************************************************/
/*										*/
/*	Holder of type information						*/
/*										*/
/********************************************************************************/

private static class VarType {

   private String type_name;
   private Map<String,String> type_attrs;

   VarType(Element xml) {
      type_name = IvyXml.getAttrString(xml,"BASE");
      type_attrs = new TreeMap<>();
      for (Element stypelt : IvyXml.children(xml,"SUBTYPE")) {
	 String nm = IvyXml.getAttrString(stypelt,"NAME");
	 String val = IvyXml.getAttrString(stypelt,"VALUE");
	 type_attrs.put(nm,val);
       }
    }

   String getName()			{ return type_name; }
   Map<String,String> getAttributes()	{ return type_attrs; }

   String getShortName() {
      String nm = type_name;
      int idx = nm.lastIndexOf(".");
      if (idx >= 0) {
	 nm = nm.substring(idx+1);
       }
      return nm;
    }

   boolean canBeNull() {
      String nullattr = type_attrs.get("CheckNullness");
      if (nullattr == null) return false;
      switch (nullattr) {
	 case "NON_NULL" :
	    return false;
	 default :
	    break;
       }
      return true;
    }

   @Override public int hashCode() {
      return type_name.hashCode() ^ type_attrs.hashCode();
    }

   @Override public boolean equals(Object o) {
      if (o instanceof VarType) {
	 VarType vo = (VarType) o;
	 if (vo.type_name.equals(type_name) &&
	       vo.type_attrs.equals(type_attrs)) return true;
       }
      return false;
    }
}	// end of inner class VarType




/********************************************************************************/
/*										*/
/*	Holder of entity information						*/
/*										*/
/********************************************************************************/

private static class VarEntity {

   private VarValue for_value;
   private int entity_id;
   private VarType entity_type;
   private boolean is_fixed;
   private boolean is_mutable;
   private String entity_kind;
   private String entity_value;
   private List<VarRef> entity_refs;
   private VarLocation entity_loc;
   private String entity_description;

   VarEntity(VarValue vv,Element xml) {
      for_value = vv;
      entity_id = IvyXml.getAttrInt(xml,"ID");
      entity_type = new VarType(IvyXml.getChild(xml,"TYPE"));
      is_fixed = IvyXml.getAttrBool(xml,"FIXED");
      is_mutable = IvyXml.getAttrBool(xml,"MUTABLE");
      entity_kind = IvyXml.getAttrString(xml,"KIND");
      entity_value = IvyXml.getTextElement(xml,"VALUE");
      entity_refs = new ArrayList<>();
      Element locxml = IvyXml.getChild(xml,"LOCATION");
      if (locxml == null) entity_loc = null;
      else entity_loc = new VarLocation(locxml);
      entity_description = IvyXml.getTextElement(xml,"DESCRIPTION");
    }

   Integer getId()					{ return entity_id; }
   VarType getType()					{ return entity_type; }

   void addReference(VarRef vr) 			{ entity_refs.add(vr); }
   List<VarRef> getReferences() 			{ return entity_refs; }

   String getShortName() {
      String s = toString();
      if (s.length() < 48) return s;
      return entity_kind;
   }

   @Override public String toString() {
      return entity_description;
    }

   String getToolTip() {
      StringBuffer buf = new StringBuffer();
      switch (entity_kind) {
	 case "STRING" :
	    if (entity_value != null) {
	       String s = entity_value.replace("\n","\\n");
	       if (s.length() >= 48+3) s = s.substring(0,48) + "...";
	       buf.append("\"" + s + "\"");
	     }
	    else {
	       buf.append("STRING");
	     }
	    break;
	 case "OBJECT" :
	 case "LOCAL" :
	 case "PROTO" :
	    if (is_fixed) {
	       buf.append("COMPUTED");
	       if (is_mutable) buf.append("*");
	       String s1 = getTypeString();
	       if (s1 != null) {
		  buf.append(" ");
		  buf.append(s1);
		}
	     }
	    else {
	       if (entity_loc != null) {
		  buf.append("NEW ");
		  buf.append(entity_type.toString());
		}
	       else if (entity_type != null) {
		  buf.append("OBJECT ");
		  buf.append(entity_type.toString());
		}
	     }
	    break;
	 default :
	    buf.append("OTHER");
	    break;
       }
      String loc = getLocationString();
      if (loc != null) buf.append(" at " + loc);
      return buf.toString();
    }

   private String getTypeString() {
      String s = null;
      if (!entity_type.equals(for_value.getType())) {
	 s = entity_type.getShortName();
	 for (Map.Entry<String,String> ent : entity_type.getAttributes().entrySet()) {
	    String key = ent.getKey();
	    if (!ent.getValue().equals(for_value.getType().getAttributes().get(key))) {
	       s += "@" + ent.getValue();
	     }
	  }
       }
      return s;
    }


   private String getLocationString() {
      if (entity_loc == null) return null;
      return entity_loc.getShortName();
    }

   @Override public int hashCode()			{ return entity_id; }

   @Override public boolean equals(Object o) {
      if (o instanceof VarEntity) {
	 VarEntity ve = (VarEntity) o;
	 return entity_id == ve.entity_id;
       }
      return false;
    }

}	// end of inner class VarEntity


/********************************************************************************/
/*										*/
/*	Holder of reference information 					*/
/*										*/
/********************************************************************************/

private static class VarRef {

   private String field_name;
   private int var_slot;
   private int stack_slot;
   private String call_id;
   private String base_id;

   VarRef(Element xml,String cid) {
       if (!IvyXml.isElement(xml,"VALUE")) xml = IvyXml.getChild(xml,"VALUE");
       field_name = IvyXml.getAttrString(xml,"FIELD");
       if (field_name == null) {
	  var_slot = IvyXml.getAttrInt(xml,"LOCAL",-1);
	  stack_slot = IvyXml.getAttrInt(xml,"STACK",-1);
	}
       else {
	  var_slot = IvyXml.getAttrInt(xml,"BASELOCAL",-1);
	  stack_slot = IvyXml.getAttrInt(xml,"BASESTACK",-1);
	}
       base_id = IvyXml.getAttrString(xml,"BASE");
       call_id = cid;
    }

   private String getCallMethod()		{ return call_id; }

   void outputXml(IvyXmlWriter xw) {
      xw.begin("REFERENCE");
      if (var_slot >= 0) xw.field("SLOT",var_slot);
      if (stack_slot >= 0) xw.field("STACK",stack_slot);
      if (field_name != null) xw.field("FIELD",field_name);
      if (base_id != null) xw.field("BASEID",base_id);
      xw.end("REFERENCE");
    }

}	// end of inner class VarRef



/********************************************************************************/
/*										*/
/*	Holder of value information						*/
/*										*/
/********************************************************************************/

private static class VarValue {

   private VarType value_type;
   private List<VarEntity> entity_set;
   private String value_kind;
   private String value_id;
   private Integer min_value;
   private Integer max_value;

   VarValue(Element xml) {
      value_kind = IvyXml.getAttrString(xml,"KIND");
      value_type = new VarType(IvyXml.getChild(xml,"TYPE"));
      entity_set = null;
      value_id = IvyXml.getAttrString(xml,"HASHID");
      Element eset = IvyXml.getChild(xml,"ENTITYSET");
      for (Element ent : IvyXml.children(eset,"ENTITY")) {
	 VarEntity ve = new VarEntity(this,ent);
	 if (entity_set == null) entity_set = new ArrayList<>();
	 entity_set.add(ve);
       }
      min_value = IvyXml.getAttrInteger(xml,"MIN");
      max_value = IvyXml.getAttrInteger(xml,"MAX");
    }

   VarType getType()				{ return value_type; }
   List<VarEntity> getEntities()		{ return entity_set; }

   String getDetails() {
      switch (value_kind) {
	 case "INT" :
	    if (min_value != null || max_value != null) {
	       String rslt = " :: ";
	       if (min_value != null) rslt += min_value;
	       else rslt += "ANY";
	       rslt += " -> ";
	       if (max_value != null) rslt += max_value;
	       else rslt += "ANY";
	       return rslt;
	     }
	    break;
       }
      return null;
    }

}	// end of inner class VarValue




/********************************************************************************/
/*										*/
/*	Holder of location information							      */
/*										*/
/********************************************************************************/

private static class VarLocation {

   private String method_name;
   private int line_number;
   private String variable_name;
   private int start_pos;
   private int node_type;
   private int after_type;
   private int after_start;

   VarLocation(Element xml) {
      method_name = IvyXml.getAttrString(xml,"METHOD");
      Element pxml = IvyXml.getChild(xml,"POINT");
      line_number = IvyXml.getAttrInt(pxml,"LINE");
      start_pos = IvyXml.getAttrInt(pxml,"START");
      node_type = IvyXml.getAttrInt(pxml,"NODETYPEID");
      after_type = IvyXml.getAttrInt(pxml,"AFTERTYPEID");
      after_start = IvyXml.getAttrInt(pxml,"AFTERSTART",-1);
      variable_name = IvyXml.getText(pxml);
      if (variable_name != null) variable_name = variable_name.trim();
    }

   String getMethodName()			{ return method_name; }
   int getLineNumber()				{ return line_number; }
   String getVariableName()			{ return variable_name; }
   int getStartPosition()			{ return start_pos; }
   int getStartNodeType()			{ return node_type; }
   int getAfterStartPosition()			{ return after_start; }
   int getAfterNodeType()			{ return after_type; }

   String getShortName() {
      int idx = method_name.lastIndexOf(".");
      if (idx >= 0) {
	 idx = method_name.lastIndexOf(".",idx-1);
       }
      String s = method_name;
      if (idx > 0) s = s.substring(idx+1);
      if (line_number > 0) s += " @ " + line_number;
      return s;
    }

}	// end of inner class VarLocation




}	// end of class BseanVarBubble




/* end of BseanVarBubble.java */

