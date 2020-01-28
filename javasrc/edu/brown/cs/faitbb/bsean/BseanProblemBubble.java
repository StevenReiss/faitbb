/********************************************************************************/
/*										*/
/*		BseanProblemBubble.java 					*/
/*										*/
/*	Bubble showing security problems for a session				*/
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowSorter;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import edu.brown.cs.bubbles.bale.BaleConstants;
import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.bass.BassFactory;
import edu.brown.cs.bubbles.bass.BassName;
import edu.brown.cs.bubbles.board.BoardColors;
import edu.brown.cs.bubbles.board.BoardProperties;
import edu.brown.cs.bubbles.board.BoardThreadPool;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.bubbles.buda.BudaBubbleArea;
import edu.brown.cs.bubbles.buda.BudaRoot;

import edu.brown.cs.faitbb.bsean.BseanConstants.BseanErrorHandler;

class BseanProblemBubble extends BudaBubble implements BseanConstants, BseanErrorHandler
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private Font base_font;
private ProblemTable problem_table;
private Map<String,List<BseanError>> error_set;
private List<BseanError> active_errors;
private int base_height;
private BseanSession for_session;


private static BoardProperties bsean_properties = BoardProperties.getProperties("Bsean");

private static String [] col_names = {
   "?", "Description", "Resource", "Line"
};


private static Class<?> [] col_types = {
   String.class, String.class, String.class, Integer.class
};


private static int [] col_sizes = { 16,200,60,50 };
private static int [] col_max_size = { 32,0,0,50 };
private static int [] col_min_size = { 12,20,20,20 };

private static final long serialVersionUID = 1;



/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

BseanProblemBubble() throws BseanException
{
   BseanSession ss = BseanFactory.getFactory().getCurrentSession();
   if (ss == null) throw new BseanException("Can't start fait");
   for_session = ss;

   base_font = bsean_properties.getFont("Bsean.problem.font");
   error_set = new HashMap<>();
   active_errors = new ArrayList<>();

   problem_table = new ProblemTable();
   ss.addErrorHandler(this);

   base_height = problem_table.getRowHeight();

   JScrollPane sp = new JScrollPane(problem_table);
   sp.setSize(new Dimension(bsean_properties.getInt(PROBLEM_WIDTH,400),
	 bsean_properties.getInt(PROBLEM_HEIGHT,150)));

   setContentPane(sp,null);

   handleErrorsUpdated();
}


@Override protected void localDispose()
{
   for_session.removeErrorHandler(this);
}



/********************************************************************************/
/*										*/
/*	Update methods								*/
/*										*/
/********************************************************************************/

@Override public void handleErrorsUpdated()
{
   BseanSession ss = BseanFactory.getFactory().getCurrentSession();
   synchronized (this) {
      if (ss != for_session) {
	 for_session.removeErrorHandler(this);
	 for_session = ss;
	 ss.addErrorHandler(this);
       }
    }

   if (for_session == null) return;

   synchronized (active_errors) {
      active_errors.clear();
      error_set.clear();
      for (BseanError be : for_session.getCurrentErrors()) {
	 String k = be.getFullKey();
	 List<BseanError> lbe = error_set.get(k);
	 if (lbe == null) {
	    lbe = new ArrayList<>();
	    error_set.put(k,lbe);
	  }
	 if (lbe.size() == 0) active_errors.add(be);
	 lbe.add(be);
       }
    }

   problem_table.modelUpdated();
}



/********************************************************************************/
/*										*/
/*	Popup menu methods							*/
/*										*/
/********************************************************************************/

@Override public void handlePopupMenu(MouseEvent e)
{
   JPopupMenu menu = new JPopupMenu();

   int r = problem_table.rowAtPoint(e.getPoint());
   if (r == 0) {
      // right click on header row
    }
   else if (r > 0) {
      BseanError be = getActualError(r-1);
      if (be != null) {
	 List<BseanError> errs = error_set.get(be.getFullKey());
	 if (errs == null) errs = Collections.singletonList(be);
	 menu.add(new BseanFactory.ExplainAction(this,errs));
       }
    }

   menu.add(getFloatBubbleAction());

   menu.show(this,e.getX(),e.getY());
}




/********************************************************************************/
/*										*/
/*	Painting methods							*/
/*										*/
/********************************************************************************/

@Override protected void paintContentOverview(Graphics2D g,Shape s)
{
   Dimension sz = getSize();

   Color c = BoardColors.getColor(PROBLEM_OVERVIEW_COLOR_PROP);
   g.setColor(c);
   g.fillRect(0,0,sz.width,sz.height);
}


@Override protected void setScaleFactor(double sf)
{
   Font ft = base_font;
   int ht = base_height;
   if (sf != 1) {
      float fsz = base_font.getSize();
      fsz = ((float)(fsz * sf));
      ft = base_font.deriveFont(fsz);
      ht = (int)(base_height * sf + 0.5);
    }
   problem_table.setFont(ft);
   problem_table.setRowHeight(ht);
}



/********************************************************************************/
/*										*/
/*	Helper methods								*/
/*										*/
/********************************************************************************/

private String getToolTip(BseanError bp)
{
   return bp.getErrorLevel().toString() + ": " + bp.getMessage();
}



private String getErrorType(BseanError bp)
{
   switch (bp.getErrorLevel()) {
      case ERROR :
	 return "E";
      case WARNING :
	 return "W";
      case NOTE :
	 return "N";
    }

   return null;
}



private String getDescription(BseanError bp)
{
   return bp.getMessage();
}



private String getResource(BseanError bp)
{
   File f = bp.getFile();
   if (f == null) return null;
   return f.getName();
}



private Integer getLine(BseanError bp)
{
   int ln = bp.getLine();
   if (ln == 0) return null;
   return Integer.valueOf(ln);
}



/********************************************************************************/
/*										*/
/*	JTable for problem display						*/
/*										*/
/********************************************************************************/

private class ProblemTable extends JTable implements MouseListener
{
   private ErrorRenderer [] error_renderer;
   private WarningRenderer [] warning_renderer;
   private NoticeRenderer [] notice_renderer;

   private static final long serialVersionUID = 1;

   ProblemTable() {
      super(new ProblemModel());
      setAutoCreateRowSorter(true);
      fixColumnSizes();
      setIntercellSpacing(new Dimension(2,1));
      setToolTipText("");
      addMouseListener(this);
      setOpaque(false);
      for (Enumeration<TableColumn> e = getColumnModel().getColumns(); e.hasMoreElements(); ) {
	 TableColumn tc = e.nextElement();
	 tc.setHeaderRenderer(new HeaderRenderer(getTableHeader().getDefaultRenderer()));
       }
      error_renderer = new ErrorRenderer[col_names.length];
      warning_renderer = new WarningRenderer[col_names.length];
      notice_renderer = new NoticeRenderer[col_names.length];
    }


   void modelUpdated() {
      ((ProblemModel) getModel()).fireTableDataChanged();
    }

   @Override public TableCellRenderer getCellRenderer(int row,int col) {
      BseanError bp = getActualError(row);
      switch (bp.getErrorLevel()) {
	 case WARNING :
	    if (warning_renderer[col] == null) {
	       warning_renderer[col] = new WarningRenderer(super.getCellRenderer(row,col));
	     }
	    return warning_renderer[col];
	 case ERROR :
	    if (error_renderer[col] == null) {
	       error_renderer[col] = new ErrorRenderer(super.getCellRenderer(row,col));
	     }
	    return error_renderer[col];
	 default :
	 case NOTE :
	    if (notice_renderer[col] == null) {
	       notice_renderer[col] = new NoticeRenderer(super.getCellRenderer(row,col));
	     }
	    return notice_renderer[col];
       }
    }

   private void fixColumnSizes() {
      TableColumnModel tcm = getColumnModel();
      for (int i = 0; i < col_sizes.length; ++i) {
	 TableColumn tc = tcm.getColumn(i);
	 tc.setPreferredWidth(col_sizes[i]);
	 if (col_max_size[i] != 0) tc.setMaxWidth(col_max_size[i]);
	 if (col_min_size[i] != 0) tc.setMinWidth(col_min_size[i]);
       }
    }

   @Override public void mouseClicked(MouseEvent e) {
      int cct = bsean_properties.getInt("Beam.problem.click.count",1);
      if (e.getClickCount() != cct) return;
      int row = rowAtPoint(e.getPoint());
      BseanError bp = getActualError(row);
      if (bp == null) return;
      File f = bp.getFile();
      int ln = bp.getLine();
      BoardThreadPool.start(new BubbleShower(f,ln));
      // showBubble(f,ln);
    }

   @Override public void mouseEntered(MouseEvent _e)			{ }
   @Override public void mouseExited(MouseEvent _e)			{ }
   @Override public void mouseReleased(MouseEvent e)			{ }
   @Override public void mousePressed(MouseEvent e)			{ }

   @Override public String getToolTipText(MouseEvent e) {
      int r = rowAtPoint(e.getPoint());
      if (r < 0) return null;
      BseanError bp = getActualError(r);
      return getToolTip(bp);
    }

   @Override protected void paintComponent(Graphics g) {
      Color tc = BoardColors.getColor(PROBLEM_TOP_COLOR_PROP);
      Color bc = BoardColors.getColor(PROBLEM_BOTTOM_COLOR_PROP);
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

}	// end of inner class ProblemTable




/********************************************************************************/
/*										*/
/*	Table model for problem table						*/
/*										*/
/********************************************************************************/

private class ProblemModel extends AbstractTableModel {

   private static final long serialVersionUID = 1;

   ProblemModel() { }

   @Override public int getColumnCount()		{ return col_names.length; }
   @Override public String getColumnName(int idx)	{ return col_names[idx]; }
   @Override public Class<?> getColumnClass(int idx)	{ return col_types[idx]; }
   @Override public boolean isCellEditable(int r,int c) { return false; }
   @Override public int getRowCount()			{ return active_errors.size(); }

   @Override public Object getValueAt(int r,int c) {
      BseanError bp;
      synchronized (active_errors) {
	 if (r < 0 || r >= active_errors.size()) return null;
	 bp = active_errors.get(r);
       }
      switch (c) {
	 case 0 :
	    return getErrorType(bp);
	 case 1 :
	    return getDescription(bp);
	 case 2 :
	    return getResource(bp);
	 case 3 :
	    return getLine(bp);
       }
      return null;
    }

}	// end of inner class ProblemModel



/********************************************************************************/
/*										*/
/*	Sorting methods 							*/
/*										*/
/********************************************************************************/

private BseanError getActualError(int idx)
{
   if (idx < 0) return null;

   synchronized (active_errors) {
      if (problem_table != null) {
	 RowSorter<?> rs = problem_table.getRowSorter();
	 try {
	    if (rs != null) idx = rs.convertRowIndexToModel(idx);
	  }
	 catch (ArrayIndexOutOfBoundsException e) {
	    return null;
	  }
       }

      if (idx >= active_errors.size()) return null;
      return active_errors.get(idx);
    }
}



/********************************************************************************/
/*										*/
/*	Renderers								*/
/*										*/
/********************************************************************************/

private static class HeaderRenderer implements TableCellRenderer {

   private TableCellRenderer default_renderer;
   private Font bold_font;
   private Font component_font;

   HeaderRenderer(TableCellRenderer dflt) {
      default_renderer = dflt;
      bold_font = null;
      component_font = null;
    }

   @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,
	 boolean foc,int r,int c) {
      JComponent cmp = (JComponent) default_renderer.getTableCellRendererComponent(t,v,sel,foc,r,c);

      if (component_font != null && t.getFont() != component_font) bold_font = null;

      if (bold_font == null) {
	 component_font = t.getFont();
	 bold_font = component_font.deriveFont(Font.BOLD);
       }
      cmp.setFont(bold_font);
      cmp.setOpaque(false);
      return cmp;
    }

}	// end of subclass HeaderRenderer




private static class ErrorRenderer implements TableCellRenderer {

   private TableCellRenderer default_renderer;

   ErrorRenderer(TableCellRenderer dflt) {
      default_renderer = dflt;
    }

   @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,
	 boolean foc,int r,int c) {
      JComponent cmp = (JComponent) default_renderer.getTableCellRendererComponent(t,v,sel,foc,r,c);
      cmp.setForeground(BoardColors.getColor(PROBLEM_ERROR_COLOR_PROP));
      cmp.setOpaque(false);
      return cmp;
    }

}	// end of subclass ErrorRenderer




private static class WarningRenderer implements TableCellRenderer {

   private TableCellRenderer default_renderer;

   WarningRenderer(TableCellRenderer dflt) {
      default_renderer = dflt;
    }

   @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,
	 boolean foc,int r,int c) {
      JComponent cmp = (JComponent) default_renderer.getTableCellRendererComponent(t,v,sel,foc,r,c);
      cmp.setForeground(BoardColors.getColor(PROBLEM_WARNING_COLOR_PROP));
      cmp.setOpaque(false);
      return cmp;
    }

}	// end of subclass HeaderRenderer



private static class NoticeRenderer implements TableCellRenderer {

   private TableCellRenderer default_renderer;


   NoticeRenderer(TableCellRenderer dflt) {
      default_renderer = dflt;
    }

   @Override public Component getTableCellRendererComponent(JTable t,Object v,boolean sel,
	 boolean foc,int r,int c) {
      JComponent cmp = (JComponent) default_renderer.getTableCellRendererComponent(t,v,sel,foc,r,c);
      cmp.setForeground(BoardColors.getColor(PROBLEM_NOTICE_COLOR_PROP));
      cmp.setOpaque(false);
      return cmp;
    }

}	// end of subclass NoticeRenderer



/********************************************************************************/
/*										*/
/*	New bubble methods							*/
/*										*/
/********************************************************************************/

private class BubbleShower implements Runnable {

   private File for_file;
   private int	at_line;
   private BassName bass_name;

   BubbleShower(File f,int ln) {
      for_file = f;
      at_line = ln;
      bass_name = null;
    }

   @Override public void run() {
      if (bass_name == null) {
	 BaleFactory bf = BaleFactory.getFactory();
	 BaleConstants.BaleFileOverview bfo = bf.getFileOverview(null,for_file);
	 if (bfo == null) return;
	 int loff = bfo.findLineOffset(at_line);
	 int eoff = bfo.mapOffsetToEclipse(loff);

	 BassFactory bsf = BassFactory.getFactory();
	 bass_name = bsf.findBubbleName(for_file,eoff);
	 if (bass_name == null) return;

	 SwingUtilities.invokeLater(this);
       }
      else {		// in Swing thread
	 BudaBubble bb = bass_name.createBubble();
	 if (bb == null) return;
	 BudaBubbleArea bba = BudaRoot.findBudaBubbleArea(BseanProblemBubble.this);
	 if (bba != null) {
	    bba.addBubble(bb,BseanProblemBubble.this,null,PLACEMENT_LOGICAL|PLACEMENT_MOVETO);
	  }
       }
    }

}	// end of inner class BubbleShower



}	// end of class BseanProblemBubble




/* end of BseanProblemBubble.java */

