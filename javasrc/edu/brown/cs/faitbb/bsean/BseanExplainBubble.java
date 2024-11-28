/********************************************************************************/
/*										*/
/*		BseanExplainBubble.java 					*/
/*										*/
/*	Provide an explanation of a security problem				*/
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
import java.awt.FlowLayout;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Position;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.w3c.dom.Element;

import edu.brown.cs.bubbles.bale.BaleConstants;
import edu.brown.cs.bubbles.bale.BaleFactory;
import edu.brown.cs.bubbles.bale.BaleConstants.BaleFileOverview;
import edu.brown.cs.bubbles.board.BoardColors;
import edu.brown.cs.bubbles.board.BoardImage;
import edu.brown.cs.bubbles.board.BoardLog;
import edu.brown.cs.bubbles.board.BoardMetrics;
import edu.brown.cs.bubbles.board.BoardThreadPool;
import edu.brown.cs.bubbles.buda.BudaBubble;
import edu.brown.cs.bubbles.buda.BudaBubbleArea;
import edu.brown.cs.bubbles.buda.BudaErrorBubble;
import edu.brown.cs.bubbles.buda.BudaRoot;
import edu.brown.cs.bubbles.bump.BumpClient;
import edu.brown.cs.bubbles.bump.BumpLocation;
import edu.brown.cs.ivy.file.IvyFormat;
import edu.brown.cs.ivy.swing.SwingGridPanel;
import edu.brown.cs.ivy.xml.IvyXml;
import edu.brown.cs.ivy.xml.IvyXmlWriter;

class BseanExplainBubble extends BudaBubble implements BseanConstants
{


/********************************************************************************/
/*										*/
/*	Private Storage 							*/
/*										*/
/********************************************************************************/

private transient GraphNode start_node;
private transient PathNode current_path;
private PathTree	path_tree;
private PathModel	path_model;
private int		alternate_offset;
private int		alternate_width;
private transient Element query_xml;

private transient Map<GraphNode,BseanAnnot> annot_map;

private static final long serialVersionUID = 1;


/********************************************************************************/
/*										*/
/*	Constructors								*/
/*										*/
/********************************************************************************/

BseanExplainBubble(Element qelt,String msg,boolean compact) throws BseanException
{
   query_xml = qelt;
   Element gelt = IvyXml.getChild(qelt,"GRAPH");
   Map<String,GraphNode> nodemap = new HashMap<>();
   for (Element nelt : IvyXml.children(gelt,"NODE")) {
      GraphNode gn = new GraphNode(nelt);
      nodemap.put(gn.getId(),gn);
      if (gn.isStart()) start_node = gn;
    }
   for (Element nelt : IvyXml.children(gelt,"NODE")) {
      GraphNode n = nodemap.get(IvyXml.getAttrString(nelt,"ID"));
      for (Element frome : IvyXml.children(nelt,"FROM")) {
	 String id = IvyXml.getText(frome).trim();
	 GraphNode fromn = nodemap.get(id);
	 n.addFrom(fromn);
       }
      for (Element toe : IvyXml.children(nelt,"TO")) {
	 String id = IvyXml.getText(toe).trim();
	 GraphNode ton = nodemap.get(id);
	 n.addTo(ton);
       }
    }

   if (compact) compactGraph(nodemap);
   if (start_node == null) {
       BudaBubble bb = new BudaErrorBubble("No flow was found for this item");
       setContentPane(bb.getContentPane());
       return;
    }

   sortGraphNodes(nodemap.values());
   current_path = new PathNode(start_node,null);
   current_path.nextPath();
   path_model = new PathModel(current_path);
   path_tree = new PathTree(path_model);

   alternate_offset = -1;
   alternate_width = -1;

   String d = msg;
   Element ptelt = IvyXml.getChild(qelt,"POINT");
   if (msg == null) {
      Element errelt = IvyXml.getChild(qelt,"ERROR");
      ptelt = IvyXml.getChild(errelt,"POINT");
      d = IvyXml.getTextElement(errelt,"MESSAGE");
    }
   d = "Explain: " + d;

   SwingGridPanel pnl = new PathPanel();
   pnl.beginLayout();
   JLabel lbl = pnl.addBannerLabel(d);
   lbl.setOpaque(false);
   pnl.addDescription("In Method",IvyXml.getAttrString(qelt,"METHOD"));
   pnl.addDescription("At Line",IvyXml.getAttrString(ptelt,"LINE"));
   pnl.addSeparator();
   JScrollPane jsp = new JScrollPane(path_tree);
   jsp.setOpaque(false);
   jsp.setBackground(BoardColors.transparent());
   JViewport jvp = jsp.getViewport();
   jvp.setOpaque(false);
   jvp.setBackground(BoardColors.transparent());

   pnl.addLabellessRawComponent(null,jsp,true,true);
   // pnl.addLabellessRawComponent(null,path_tree,true,true);

   pnl.addBottomButton("Show Code","SHOWCODE",new ShowAllCodeAction());
   JButton btn = pnl.addBottomButton("Create Test Case","TESTCASE",new TestCaseAction());
   btn.setEnabled(false);
   pnl.addBottomButtons();

   annot_map = new HashMap<>();

   setContentPane(pnl);
}



@Override protected void localDispose()
{
   if (annot_map == null) return;
   
   for (BseanAnnot an : annot_map.values()) {
      BaleFactory.getFactory().removeAnnotation(an);
    }
   annot_map.clear();
}




/********************************************************************************/
/*										*/
/*	Bubble methods								*/
/*										*/
/********************************************************************************/

@Override public void handlePopupMenu(MouseEvent evt)
{
   JPopupMenu menu = new JPopupMenu();
   GraphNode gn = getGraphNode(evt,getContentPane().getParent());
   if (gn != null) {
      menu.add(new ShowCodeAction(gn));
    }
   menu.add(getFloatBubbleAction());
   menu.show(this,evt.getX(),evt.getY());
}



private GraphNode getGraphNode(MouseEvent evt,Component c)
{
   if (path_tree == null) return null;

   GraphNode gn = null;
   if (c == null) c = evt.getComponent();
   Point pt = SwingUtilities.convertPoint(c,evt.getPoint(),path_tree);
   int row = path_tree.getRowForLocation(pt.x,pt.y);
   if (row > 0) {
      TreePath tp = path_tree.getPathForRow(row);
      DefaultMutableTreeNode mtn = (DefaultMutableTreeNode) tp.getLastPathComponent();
      Object uo = (mtn == null ? null : mtn.getUserObject());
      if (uo != null && uo instanceof MethodNode) {
	 MethodNode mn = (MethodNode) uo;
	 gn = mn.getBaseNode();
       }
      else if (uo != null && uo instanceof PathNode) {
	 PathNode pn = (PathNode) uo;
	 gn = pn.getGraphNode();
       }
    }
   return gn;
}




private PathNode getPathNode(MouseEvent evt,Component c)
{
   if (path_tree == null) return null;

   if (c == null) c = evt.getComponent();
   Point pt = SwingUtilities.convertPoint(c,evt.getPoint(),path_tree);
   int row = path_tree.getRowForLocation(pt.x,pt.y);
   if (row > 0) {
      TreePath tp = path_tree.getPathForRow(row);
      DefaultMutableTreeNode mtn = (DefaultMutableTreeNode) tp.getLastPathComponent();
      Object uo = (mtn == null ? null : mtn.getUserObject());
     if (uo != null && uo instanceof PathNode) {
	 PathNode pn = (PathNode) uo;
	 return pn;
       }
    }
   return null;
}



/********************************************************************************/
/*										*/
/*	Sort to ensure loops are avoided on next path				*/
/*										*/
/********************************************************************************/

// the from nodes of a node need to be ordered so that taking the first node
// will always take you to an END node without loops

private void sortGraphNodes(Collection<GraphNode> nodes)
{
   Map<GraphNode,Integer> nodevals = new HashMap<>();
   Set<GraphNode> done = new HashSet<>();
   List<GraphNode> stack = new ArrayList<>();
   // first assign a value of 0 to all nodes on straight path to the root
   sortDfs(start_node,stack,nodevals,done);
   // handle weird graphs that have unreachable items -- avoid infinite loops
   int maxtry = 5;
   for (int i = 0; i < maxtry; ++i) {
      if (isDone(nodes,nodevals)) break;
      if (i == maxtry-1) {
         for (Iterator<GraphNode> it = nodes.iterator(); it.hasNext(); ) {
            GraphNode gn = it.next();
            if (nodevals.get(gn) == null) {
               BoardLog.logD("BSEAN","Remvoe unreachable node " + gn.getNodeId());
               it.remove();
             }
          }
       }
      else {
         done.clear();
         assignDfs(start_node,nodevals,done);
       }
    }
   

   NodeComparator nc = new NodeComparator(nodevals);

   for (GraphNode gn : nodes) {
      List<GraphNode> from = gn.getFromNodes();
      Collections.sort(from,nc);
    }
}

private boolean isDone(Collection<GraphNode> nodes,Map<GraphNode,Integer> nodeval) 
{
   for (GraphNode gn : nodes) {
       if (nodeval.get(gn) == null) return false;
    }
   return true;
}


private void compactGraph(Map<String,GraphNode> nodemap)
{
   for (Iterator<GraphNode> it = nodemap.values().iterator(); it.hasNext(); ) {
      GraphNode gn = it.next();
      if (gn.remove()) {
	 it.remove();
       }
    }
}



private void sortDfs(GraphNode gn,List<GraphNode> stack,
      Map<GraphNode,Integer> vals,Set<GraphNode> done)
{
   if (gn == null) return;
   if (!done.add(gn)) return;
   if (gn.getFromNodes() == null) return;

   stack.add(gn);

   for (GraphNode ngn : gn.getFromNodes()) {
      if (ngn.isEnd()) {
	 vals.put(ngn,0);
	 int nval =  stack.size();
	 for (GraphNode sgn : stack) {
	    Integer oval = vals.get(sgn);
	    if (oval == null) vals.put(sgn,nval);
	    else vals.put(sgn,Math.min(oval,nval));
	    --nval;
	  }
       }
      else {
	 sortDfs(ngn,stack,vals,done);
       }
    }

   stack.remove(gn);
}


private boolean assignDfs(GraphNode gn,Map<GraphNode,Integer> vals,Set<GraphNode> done)
{
   if (!done.add(gn)) return false;

   boolean sts = false;
   int min = -1;
   for (GraphNode ngn : gn.getFromNodes()) {
      sts |= assignDfs(ngn,vals,done);
      Integer val = vals.get(ngn);
      if (val != null) {
	 if (min < 0) min = val;
	 else if (min > val) min = val;
       }
    }
   if (vals.get(gn) == null) {
      if (min < 0) {
	 if (gn.getFromNodes().size() == 0) min = vals.size();
	 else return true;
       }
      vals.put(gn,min+1);
    }
   return sts;
}



private class NodeComparator implements Comparator<GraphNode> {

   private Map<GraphNode,Integer> node_vals;

   NodeComparator(Map<GraphNode,Integer> vals) {
      node_vals = vals;
    }

    @Override public int compare(GraphNode g1,GraphNode g2) {
      Integer v1 = node_vals.get(g1);
      Integer v2 = node_vals.get(g2);
      if (v1 == null) {
         BoardLog.logD("BSEAN","Missing value for node " + g1);
         return -1;
       }
      if (v2 == null) {
         BoardLog.logD("BSEAN","Missing value for node " + g2);
         return 1;
       }
      if (v1 < v2) return -1;
      if (v1 > v2) return 1;
      v1 = g1.getNodeId();
      v2 = g2.getNodeId();
      return Integer.compare(v1,v2);
    }
}




/********************************************************************************/
/*										*/
/*	Graph Node								*/
/*										*/
/********************************************************************************/

private static class GraphNode {

   private String node_id;
   private int id_number;
   private String node_reason;
   private String call_id;
   private String node_method;
   private String node_file;
   private String method_description;
   private int start_offset;
   private int end_offset;
   private int ins_offset;
   private String after_id;
   private int after_start;
   private int line_number;
   private String node_text;
   private List<GraphNode> to_nodes;
   private List<GraphNode> from_nodes;
   private boolean is_start;
   private boolean is_end;

   GraphNode(Element xml) {
      node_id = IvyXml.getAttrString(xml,"ID");
      id_number = IvyXml.getAttrInt(xml,"ID");
      node_reason = IvyXml.getAttrString(xml,"REASON");
      call_id = IvyXml.getAttrString(xml,"CALLID");
      node_method = IvyXml.getAttrString(xml,"METHOD");
      node_file = IvyXml.getAttrString(xml,"FILE");
      method_description = IvyXml.getAttrString(xml,"SIGNATURE");
      is_start = IvyXml.getAttrBool(xml,"START");
      is_end = IvyXml.getAttrBool(xml,"END");

      Element pt = IvyXml.getChild(xml,"POINT");
      start_offset = IvyXml.getAttrInt(pt,"START");
      end_offset = IvyXml.getAttrInt(pt,"END");
      after_id = IvyXml.getAttrString(xml,"AFTER");
      after_start = IvyXml.getAttrInt(xml,"AFTERSTART");
      line_number = IvyXml.getAttrInt(pt,"LINE");
      ins_offset = IvyXml.getAttrInt(pt,"LOC");
      node_text = IvyXml.getText(pt);

      to_nodes = new ArrayList<>();
      from_nodes = new ArrayList<>();
    }

   String getId()				{ return node_id; }
   int getNodeId()				{ return id_number; }
   boolean isStart()				{ return is_start; }
   boolean isEnd()				{ return is_end; }
   String getCallId()				{ return call_id; }

   void addFrom(GraphNode gn) {
      boolean added = false;
      if (gn.getNodeId() > getNodeId()) {
	 for (int i = 0; i < from_nodes.size(); ++i) {
	    GraphNode ngn = from_nodes.get(i);
	    if (ngn.getNodeId() <= getNodeId()) {
	       from_nodes.add(i,gn);
	       added = true;
	       break;
	     }
	  }
       }
      if (!added) from_nodes.add(gn);
   }
   void addTo(GraphNode gn)			{ to_nodes.add(gn); }
   List<GraphNode> getFromNodes()		{ return from_nodes; }

   int getLineNumber()				{ return line_number; }
   String getReason()				{ return node_reason; }

   String getMethod()				{ return node_method; }
   String getDescription()			{ return method_description; }
   String getFile()				{ return node_file; }

   int getStartOffset() 			{ return start_offset; }

   boolean canRemove() {
      if (to_nodes.size() != 1) return false;
      GraphNode gn = to_nodes.get(0);
      if (gn.from_nodes.size() != 1) return false;
      if (gn.from_nodes.get(0) != this) return false;
   
      if (line_number != gn.line_number) return false;
      if (!node_reason.equals(gn.node_reason)) return false;
      if (!method_description.equals(gn.method_description)) return false;
      if (!node_file.equals(gn.node_file)) return false;
      if (!node_method.equals(gn.node_method)) return false;
   
      return true;
    }

   boolean remove() {
      if (!canRemove()) return false;
      GraphNode gn = to_nodes.get(0);
      BoardLog.logD("BSEAN","Remove node " + node_id + " " + gn.node_id);
      gn.from_nodes = from_nodes;
      return true;
    }

   void outputXml(IvyXmlWriter xw) {
      xw.begin("NODE");
      xw.field("ID",node_id);
      xw.field("REASON",node_reason);
      xw.field("CALLID",call_id);
      xw.field("METHOD",node_method);
      xw.field("FILE",node_file);
      xw.field("SIGNATURE",method_description);
      xw.field("START",is_start);
      xw.field("END",is_end);
      xw.begin("POINT");
      xw.field("START",start_offset);
      xw.field("END",end_offset);
      xw.field("AFTER",after_id);
      xw.field("AFTERSTART",after_start);
      xw.field("LOC",ins_offset);
      xw.field("LINE",line_number);
      xw.text(node_text);
      xw.end("POINT");
      xw.end("NODE");
    }

   @Override public String toString() {
      return node_id + ": " + line_number + "@" + getMethod();
    }

}	// end of inner class GraphNode


/********************************************************************************/
/*										*/
/*	Path Node								*/
/*										*/
/********************************************************************************/

private static class PathNode {

   private GraphNode graph_node;
   private PathNode next_node;

   PathNode(GraphNode gn,PathNode to) {
      graph_node = gn;
      next_node = null;
      if (to != null) to.next_node = this;
    }

   PathNode getNext()				{ return next_node; }
   GraphNode getGraphNode()			{ return graph_node; }

   boolean hasAlternatives() {
      if (graph_node == null) return false;
      List<GraphNode> frm = graph_node.getFromNodes();
      if (frm != null && frm.size() >= 2) return true;
      return false;
    }

   void nextPath() {
      if (graph_node == null) return;
      List<GraphNode> frm = graph_node.getFromNodes();
      if (next_node != null) {
         GraphNode gn = next_node.graph_node;
         int idx = frm.indexOf(gn);
         idx = idx+1;
         if (idx >= frm.size()) idx = 0;
         GraphNode ngn = frm.get(idx);
         buildPath(ngn);
       }
      else if (frm.size() >= 1) {
         GraphNode gn = frm.get(0);
         buildPath(gn);
       }
   
    }

   void buildPath(GraphNode gn) {
      PathNode pn = new PathNode(gn,this);
      pn.nextPath();
    }

}	// end of inner class PathNode





private static final class MethodNode {

   private GraphNode base_node;
   private int start_index;
   private int end_index;

   private MethodNode(GraphNode gn,int start) {
      base_node = gn;
      start_index = start;
      end_index = start;
    }

   int getStart()				{ return start_index; }
   int getEnd() 				{ return end_index; }
   GraphNode getBaseNode()			{ return base_node; }

   void resetStart(int idx) {
      start_index = idx;
      end_index = idx;
    }

   void extendTo(int idx) {
      end_index = idx;
    }

   boolean sameMethod(GraphNode gn) {
      if (gn.getCallId().equals(base_node.getCallId())) return true;
      return false;
    }

}	// end of inner class MethodNode




/********************************************************************************/
/*										*/
/*	Tree model for current path						*/
/*										*/
/********************************************************************************/

private static class PathModel extends DefaultTreeModel {

   private transient PathNode start_node;
   private transient List<PathNode> path_list;
   private transient List<MethodNode> method_list;

   private static final long serialVersionUID = 1;

   PathModel(PathNode start) {
      super(new DefaultMutableTreeNode());
      start_node = start;
      method_list = new ArrayList<>();
      buildDataModel();
      buildTreeModel();
    }

   void update() {
      buildDataModel();
      buildTreeModel();
   }

   private void buildDataModel() {
      path_list = new ArrayList<>();
      for (PathNode pn = start_node; pn != null; pn = pn.getNext()) {
	 path_list.add(pn);
       }
      method_list.clear();		// might work without this
      int midx = 0;
      for (int i = 0; i < path_list.size(); ++i) {
	 PathNode pn = path_list.get(i);
	 for ( ; ; ) {
	    if (midx >= method_list.size()) {
	       MethodNode mn = new MethodNode(pn.getGraphNode(),i);
	       method_list.add(mn);
	       break;
	     }
	    else {
	       MethodNode mn = method_list.get(midx);
	       if (mn.sameMethod(pn.getGraphNode())) {
		  mn.extendTo(i);
		  break;
		}
	       else {
		  ++midx;
		  if (midx < method_list.size()) {
		     MethodNode mn1 = method_list.get(midx);
		     if (!mn1.sameMethod(pn.getGraphNode())) {
			while (method_list.size() >= midx) {
			   method_list.remove(midx);
                         }
		      }
		     else mn1.resetStart(i);
		   }
		}
	     }
	  }
       }
    }

   private void buildTreeModel() {
      DefaultMutableTreeNode mtn = (DefaultMutableTreeNode) getRoot();
      int nidx = 0;
      for (MethodNode mn : method_list) {
	 DefaultMutableTreeNode ctn = null;
	 if (mtn.getChildCount() > nidx) {
	    ctn = (DefaultMutableTreeNode) mtn.getChildAt(nidx);
	    MethodNode cmn = (MethodNode) ctn.getUserObject();
	    if (!mn.sameMethod(cmn.getBaseNode())) {
	       ctn = null;
	       while (mtn.getChildCount() > nidx) {
		  removeNodeFromParent((MutableTreeNode) mtn.getChildAt(nidx));
		}
	     }
	    else ++nidx;
	  }
	 if (ctn == null) {
	    ctn = new DefaultMutableTreeNode(mn,true);
	    mtn.add(ctn);
	    int [] idxes = new int [] { nidx++ };
	    nodesWereInserted(mtn,idxes);
	  }
	 for (int i = mn.getStart(); i <= mn.getEnd(); ++i) {
	    PathNode pn = path_list.get(i);
	    int lidx = i - mn.getStart();
	    DefaultMutableTreeNode ltn = null;
	    if (ctn.getChildCount() > lidx) {
	       ltn = (DefaultMutableTreeNode) ctn.getChildAt(lidx);
	       if (ltn.getUserObject() != pn) {
		  ltn = null;
		  while (ctn.getChildCount() > lidx) {
		     removeNodeFromParent((MutableTreeNode) ctn.getChildAt(lidx));
		   }
		}
	     }
	    if (ltn == null) {
	       ltn = new DefaultMutableTreeNode(pn,false);
	       ctn.add(ltn);
	       int [] idxes = new int [] { lidx };
	       nodesWereInserted(ctn,idxes);
	     }
	  }
       }
    }

}	// end of inner class PathModel



/********************************************************************************/
/*										*/
/*	Tree display								*/
/*										*/
/********************************************************************************/

private class PathTree extends JTree {

   private static final long serialVersionUID = 1;

   PathTree(PathModel pm) {
      super(pm);
      setCellRenderer(new PathCellRenderer());
      setDragEnabled(false);
      setEditable(false);
      setExpandsSelectedPaths(true);
      setRootVisible(false);
      setShowsRootHandles(true);
      setToggleClickCount(1);
      setVisibleRowCount(10);
      getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
      setOpaque(false);
      setBackground(BoardColors.transparent());
      initialExpand();
      addMouseListener(new TreeMouser());
      addTreeSelectionListener(new TreeSelector());
      setToolTipText("Security Error Path Viewer");
    }

   @Override public boolean getScrollableTracksViewportWidth()	{ return true; }

   @Override public String getToolTipText(MouseEvent evt) {
      GraphNode gn = getGraphNode(evt,null);
      if (gn != null) {
	 return gn.getMethod() + " : " + gn.getLineNumber() + " :: " + gn.getReason();
       }
      return null;
    }

   @Override public void paintComponent(Graphics g) {
      super.paintComponent(g);
    }

   void initialExpand() {
      for (int i = 0; i < getRowCount() && i < 40; ++i) {
	 TreePath tp = getPathForRow(i);
	 expandPath(tp);
       }
    }
}	// end of inner class PathTree





private static class PathPanel extends SwingGridPanel {

   private static final long serialVersionUID = 1;

   PathPanel() {
      setOpaque(false);
      BoardColors.setColors(this,EXPLAIN_TOP_COLOR_PROP);
    }

   @Override protected void paintComponent(Graphics g) {
      Color tc = BoardColors.getColor(EXPLAIN_TOP_COLOR_PROP);
      Color bc = BoardColors.getColor(EXPLAIN_BOTTOM_COLOR_PROP);
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

}	// end of inner class PathPanel




/********************************************************************************/
/*										*/
/*	Mouse handling								*/
/*										*/
/********************************************************************************/

private final class TreeMouser extends MouseAdapter {

   @Override public void mouseClicked(MouseEvent evt) {
     if (evt.getButton() == MouseEvent.BUTTON1) {
	PathNode pn = getPathNode(evt,null);
	if (pn == null) return;
	if (!pn.hasAlternatives() || alternate_offset < 0) return;
	if (evt.getX() >= alternate_offset && evt.getX() < alternate_offset + alternate_width) {
	   pn.nextPath();
	   path_model.update();
	   path_tree.initialExpand();
	 }
       }
    }
}



/********************************************************************************/
/*										*/
/*	Selection and highlighting						*/
/*										*/
/********************************************************************************/

private final class TreeSelector implements TreeSelectionListener {

   @Override public void valueChanged(TreeSelectionEvent e) {
      TreePath [] tps = e.getPaths();
      for (TreePath tp : tps) {
	 DefaultMutableTreeNode mtn = (DefaultMutableTreeNode) tp.getLastPathComponent();
	 if (mtn == null) continue;
	 Object o = mtn.getUserObject();
	 List<GraphNode> gns = new ArrayList<>();
	 if (mtn.getChildCount() == 0) {
	    if (o instanceof PathNode) {
	       PathNode pn = (PathNode) o;
	       gns.add(pn.getGraphNode());
	     }
	  }
	 else {
	    for (int i = 0; i < mtn.getChildCount(); ++i) {
	       DefaultMutableTreeNode ctn = (DefaultMutableTreeNode) mtn.getChildAt(i);
	       Object co = ctn.getUserObject();
	       if (co instanceof PathNode) {
		  PathNode pn = (PathNode) co;
		  gns.add(pn.getGraphNode());
		}
	     }
	  }

	 for (GraphNode gn : gns) {
	    BseanAnnot an = annot_map.get(gn);
	    if (e.isAddedPath(tp)) {
	       if (an == null) {
		  an = new BseanAnnot(gn);
		  annot_map.put(gn,an);
		  BaleFactory.getFactory().addAnnotation(an);
		}
	     }
	    else {
	       if (an != null) {
		  BaleFactory.getFactory().removeAnnotation(an);
		  annot_map.remove(gn);
		}
	     }
	  }
       }
    }

}	// end of inner class TreeSelector







private class BseanAnnot implements BaleConstants.BaleAnnotation {

   private GraphNode graph_node;
   private BaleFileOverview for_document;
   private Position execute_pos;
   private Color annot_color;
   private File for_file;

   BseanAnnot(GraphNode gn) {
      graph_node = gn;
      for_file = new File(graph_node.getFile());
      annot_color = BoardColors.getColor(EXPLAIN_ANNOT_COLOR_PROP);
      for_document = BaleFactory.getFactory().getFileOverview(null,for_file,false);
      execute_pos = null;
      try {
	 execute_pos = for_document.createPosition(gn.getStartOffset());
       }
      catch (BadLocationException ex) {
	 BoardLog.logE("BSEAN","Bad graph node position",ex);
       }
    }

   @Override public int getDocumentOffset()	{ return execute_pos.getOffset(); }
   @Override public File getFile()		{ return for_file; }

   @Override public Icon getIcon(BudaBubble b) {
      return null;
    }

   @Override public String getToolTip() {
      if (execute_pos == null) return null;
      return "Error data flow " + graph_node.getLineNumber();
    }

   @Override public Color getLineColor(BudaBubble bbl) {
      return annot_color;
    }

   @Override public Color getBackgroundColor()			{ return null; }

   @Override public boolean getForceVisible(BudaBubble bb) {
      BudaBubbleArea bba = BudaRoot.findBudaBubbleArea(bb);
      BudaBubbleArea bba1 = BudaRoot.findBudaBubbleArea(BseanExplainBubble.this);
      if (bba != bba1) return false;
      return false;			// don't force this line to be visible
    }

   @Override public int getPriority()				{ return 20; }

   @Override public void addPopupButtons(Component c,JPopupMenu m) { }

}



/********************************************************************************/
/*										*/
/*	Cell rendering								*/
/*										*/
/********************************************************************************/

private class PathCellRenderer extends JPanel implements TreeCellRenderer {

   private DefaultTreeCellRenderer default_renderer;
   private JLabel open_label;
   private JLabel alternate_label;
   private JLabel description_label;
   private transient Icon alternate_icon;

   private static final long serialVersionUID = 1;

   PathCellRenderer() {
      default_renderer = new DefaultTreeCellRenderer();
      setOpaque(true);
      setBackground(BoardColors.transparent());
      setLayout(new FlowLayout(FlowLayout.LEFT,2,2));
      open_label = new JLabel(default_renderer.getDefaultOpenIcon());
      fixSize(open_label);
      add(open_label);
      alternate_icon = BoardImage.getIcon("alternative");
      alternate_label = new JLabel(alternate_icon);
      fixSize(alternate_label);
      add(alternate_label);
      description_label = new JLabel();
      add(description_label);
      validate();
      alternate_offset = -1;
      alternate_width = -1;
    }

   @Override public Component getTreeCellRendererComponent(JTree tree,
	 Object value,boolean selected,boolean expanded,boolean leaf,
	 int row,boolean hasfocus) {
      if (value instanceof DefaultMutableTreeNode) {
	 DefaultMutableTreeNode mtn = (DefaultMutableTreeNode) value;
	 value = mtn.getUserObject();
       }
      if (leaf && value instanceof PathNode) {
	 PathNode pn = (PathNode) value;
	 GraphNode gn = pn.getGraphNode();
	 open_label.setIcon(default_renderer.getDefaultLeafIcon());
	 if (pn.hasAlternatives()) alternate_label.setIcon(alternate_icon);
	 else alternate_label.setIcon(null);
	 String d = gn.getLineNumber() + ": " + gn.getReason();
	 description_label.setText(d);
       }
      else if (value != null) {
	 MethodNode mn = (MethodNode) value;
	 GraphNode gn = mn.getBaseNode();
	 if (expanded) open_label.setIcon(default_renderer.getDefaultOpenIcon());
	 else open_label.setIcon(default_renderer.getDefaultClosedIcon());
	 alternate_label.setIcon(null);
	 if (gn != null) {
	    String d = gn.getMethod();
	    description_label.setText(d);
	  }
       }
      if (selected) {
	 setOpaque(true);
	 setBackground(default_renderer.getBackgroundSelectionColor());
       }
      else {
	 setOpaque(false);
	 setBackground(BoardColors.transparent());
       }

      return this;
    }

   private void fixSize(JComponent c) {
      Dimension sz = c.getPreferredSize();
      c.setSize(sz);
      c.setMinimumSize(sz);
      c.setMaximumSize(sz);
    }

   @Override public void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (alternate_offset < 0 && alternate_label.getWidth() > 0) {
	 alternate_offset = getX() + alternate_label.getX();
	 alternate_width = alternate_label.getWidth();
       }
    }

}	// end of inner class PathCellRenderer




/********************************************************************************/
/*										*/
/*	Action methods								*/
/*										*/
/********************************************************************************/

private class ShowCodeAction extends AbstractAction {

   private transient GraphNode graph_node;

   private static final long serialVersionUID = 1;

   ShowCodeAction(GraphNode gn) {
      super("Open Editor for " + gn.getMethod());
      graph_node = gn;
    }

   @Override public void actionPerformed(ActionEvent evt) {
      BoardMetrics.noteCommand("BSEAN","GotoSource");
      BudaBubbleArea bba = BudaRoot.findBudaBubbleArea(BseanExplainBubble.this);
      String proj = null;
      String d = graph_node.getDescription();
      String d1 = d.substring(1,d.indexOf(")"));
      String d2 = IvyFormat.formatTypeNames(d1,",");
      String mid = graph_node.getMethod() + "(" + d2 + ")";
      BudaBubble bb = BaleFactory.getFactory().createMethodBubble(proj,mid);
      if (bb == null) return;
      bba.addBubble(bb,BseanExplainBubble.this,null,
	    PLACEMENT_PREFER|PLACEMENT_GROUPED|PLACEMENT_MOVETO);
    }

}	// end of inner class ShowCodeAction



private final class ShowAllCodeAction implements ActionListener {

   @Override public void actionPerformed(ActionEvent evt) {
      BoardMetrics.noteCommand("BSEAN","ShowAllCode");
      BumpClient bc = BumpClient.getBump();
      String proj = null;
      Set<String> done = new HashSet<>();
      List<BumpLocation> locs = new ArrayList<>();
      for (PathNode pn = current_path; pn != null; pn = pn.getNext()) {
	 GraphNode gn = pn.getGraphNode();
	 String d = gn.getDescription();
	 String d1 = d.substring(1,d.indexOf(")"));
	 String d2 = IvyFormat.formatTypeNames(d1,",");
	 String mid = gn.getMethod() + "(" + d2 + ")";
	 if (done.add(mid)) {
	    List<BumpLocation> mlocs = bc.findMethod(proj,mid,false);
	    if (mlocs != null) locs.addAll(mlocs);
	  }
       }
      BaleFactory.getFactory().createBubbleStack(BseanExplainBubble.this,null,null,true,
	    locs,BudaLinkStyle.STYLE_SOLID);
    }

}	// end of inner class ShowCodeAction




/********************************************************************************/
/*										*/
/*	Action to try creating a test case					*/
/*										*/
/********************************************************************************/

private class TestCaseAction implements ActionListener, Runnable {

   private String path_text;

   TestCaseAction() {
      path_text = null;
    }

   @Override public void actionPerformed(ActionEvent evt) {
      BoardMetrics.noteCommand("BSEAN","CreateTestCase");
      IvyXmlWriter xw = new IvyXmlWriter();
      xw.begin("TESTCASE");
      xw.writeXml(query_xml);
      xw.begin("PATH");
      for (PathNode pn = current_path; pn != null; pn = pn.getNext()) {
	 GraphNode gn = pn.getGraphNode();
	 gn.outputXml(xw);
       }
      xw.end("PATH");
      xw.end("TESTCASE");
      path_text = xw.toString();

      BoardThreadPool.start(this);
    }

   @Override public void run() {
      Element rslt = BseanFactory.getFactory().sendFaitMessage(null,"TESTCASE",null,path_text);
      BoardLog.logD("BSEAN","Test case response: " + rslt);
    }

}	// end of inner class TestCaseAction



}	// end of class BseanExplainBubble




/* end of BseanExplainBubble.java */

