/* vim: set ts=2: */
/**
 * Copyright (c) 2010 The Regents of the University of California.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *   1. Redistributions of source code must retain the above copyright
 *      notice, this list of conditions, and the following disclaimer.
 *   2. Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions, and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *   3. Redistributions must acknowledge that this software was
 *      originally developed by the UCSF Computer Graphics Laboratory
 *      under support by the NIH National Center for Research Resources,
 *      grant P41-RR01081.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package edu.ucsf.rbvi.enhancedGraphics.internal.charts;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// System imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

// Cytoscape imports

/**
 * The NodeChartViewer creates the actual custom graphics
 */
public class ViewUtils {

	public enum Position {
		CENTER ("center"),
		EAST ("east"),
		NORTH ("north"),
		NORTHEAST ("northeast"),
		NORTHWEST ("northwest"),
		SOUTH ("south"),
		SOUTHEAST ("southeast"),
		SOUTHWEST ("southwest"),
		WEST ("west");
	
		private String label;
		private static Map<String, Position>pMap;
	
		Position(String label) { 
			this.label = label; 
			addPosition(this);
		}
	
		public String getLabel() {
			return label;
		}

		public String toString() {
			return label;
		}
	
		private void addPosition(Position pos) {
			if (pMap == null) pMap = new HashMap<String,Position>();
			pMap.put(pos.getLabel(), pos);
		}
	
		static public Position getPosition(String label) {
			if (pMap.containsKey(label))
				return pMap.get(label);
			return null;
		}
	}

	/**
 	 * getPosition will return either a Point2D or a Position, depending on whether
 	 * the user provided us with a position keyword or a specific value.
 	 *
 	 * @param position the position argument
 	 * @return a Point2D representing the X,Y offset specified by the user or a Position
 	 * enum that corresponds to the provided keyword.  <b>null</b> is returned if the input
 	 * is illegal.
 	 */
	public static Object getPosition(String position) {
		Position pos = Position.getPosition(position);
		if (pos != null) 
			return pos;

		String [] xy = position.split(",");
		if (xy.length != 2) {
			return null;
		}

		try {
			Double x = Double.valueOf(xy[0]);
			Double y = Double.valueOf(xy[1]);
			return new Point2D.Double(x.doubleValue(), y.doubleValue());
		} catch (NumberFormatException e) {
			return null;
		}
	}


	public static final String DEFAULT_FONT=Font.SANS_SERIF;
	public static final int DEFAULT_STYLE=Font.PLAIN;
	public static final int DEFAULT_SIZE=8;
	public static final double DEFAULT_LABEL_WIDTH = Double.MAX_VALUE; // By default there is no restriction
	public static final double DEFAULT_LABEL_LINE_SPACING = 1.0f;

	public static enum TextAlignment {ALIGN_NONE, ALIGN_LEFT, ALIGN_CENTER, ALIGN_CENTER_TOP, 
	                                  ALIGN_RIGHT, ALIGN_CENTER_BOTTOM, ALIGN_MIDDLE};

	public static final HashMap<String, ViewUtils.TextAlignment> textAlignmentMapping;

	static {
		textAlignmentMapping = new HashMap<String, ViewUtils.TextAlignment>(8);
		textAlignmentMapping.put("none", TextAlignment.ALIGN_NONE);
		textAlignmentMapping.put("left", TextAlignment.ALIGN_LEFT);
		textAlignmentMapping.put("center", TextAlignment.ALIGN_CENTER);
		textAlignmentMapping.put("center_top", TextAlignment.ALIGN_CENTER_TOP);
		textAlignmentMapping.put("right", TextAlignment.ALIGN_RIGHT);
		textAlignmentMapping.put("center_bottom", TextAlignment.ALIGN_CENTER_BOTTOM);
		textAlignmentMapping.put("middle", TextAlignment.ALIGN_MIDDLE);
	};

	// ML: Modified to wrap text
	// ML TODO: barchart labels are weirdly aligned
	public static Shape getLabelShape(String label, Font font, double maxWidth, double lineSpacing) {
		if(label == null) {
			return null;
		}
		
		// Get the canvas so that we can find the graphics context
		FontRenderContext frc = new FontRenderContext(null, false, false);

		// We will try to put the label into the shape character by character
		char labelChars[] = label.toCharArray();
		int firstCharNextLine = 0; // offset of the first character of the next line
		int labelLength = label.length();
		int lastCharNextLine = labelLength; // offset of the last character of the next line (not included)
		
		// TextLayout does not take new lines into account
		// We have to create the Shape containing all the lines ourselves
		Area labelShape = new Area();
		while(firstCharNextLine < labelLength) {
			String labelLine = new String(labelChars, firstCharNextLine, lastCharNextLine-firstCharNextLine);
			
			// We create the TextLayout for the line
			TextLayout lineTL = new TextLayout(labelLine, font, frc);
			
			// We check if the width is OK (1 char per line is always OK)
			if(labelLine.length() == 1 || lineTL.getBounds().getWidth() <= maxWidth) {
				// no margin top for the first line, otherwise we add the defined line spacing
				double marginTop = (labelShape.isEmpty() ? 0.0f : lineSpacing);
				
				// We create the Shape for the line by moving down the TextLayout Shape by the current height of the label Shape
				Shape lineShape = lineTL.getOutline(AffineTransform.getTranslateInstance(0, labelShape.getBounds().getHeight() + marginTop));
				// We add the text shape to the label shape
				labelShape.add(new Area(lineShape));
				
				// We reset the offsets
				firstCharNextLine = lastCharNextLine;
				lastCharNextLine = labelLength;
			} else {
				// The shape is too large, we reduce by one char
				lastCharNextLine--;
			}
		}
		
		return labelShape;
	}

	public static Shape getLabelShape(String label, String fontName, 
	                                  int fontStyle, int fontSize, double maxWidth, double lineSpacing) {
		if (fontName == null) fontName = DEFAULT_FONT;
		if (fontStyle == 0) fontStyle = DEFAULT_STYLE;
		if (fontSize == 0) fontSize = DEFAULT_SIZE;

		Font font = new Font(fontName, fontStyle, fontSize);
		return getLabelShape(label, font, maxWidth, lineSpacing);
	}

	public static Shape positionLabel(Shape lShape, Point2D position, TextAlignment tAlign, 
	                                  double maxHeight, double maxWidth, double rotation) {
		
		if(lShape == null) {
			return null;
		}

		// First we make sure that the lShape is in (0,0)
		AffineTransform initTrans = new AffineTransform();
		initTrans.translate(-lShape.getBounds2D().getX(), -lShape.getBounds2D().getY());
		lShape = initTrans.createTransformedShape(lShape);
		
		// Figure out how to move the text to center it on the bbox
		double textWidth = lShape.getBounds2D().getWidth(); 
		double textHeight = lShape.getBounds2D().getHeight();

		// Before we go any further, scale the text, if necessary
		if (maxHeight > 0.0 || maxWidth > 0.0) {
			double scaleWidth = 1.0;
			double scaleHeight = 1.0;
			if (maxWidth > 0.0 && textWidth > maxWidth)
				scaleWidth = maxWidth/textWidth * 0.9;
			if (maxHeight > 0.0 && textHeight > maxHeight)
				scaleHeight = maxHeight/textHeight * 0.9;

			double scale = Math.min(scaleWidth, scaleHeight);

			// We don't want to scale down too far.  If scale < 20% of the font size, skip the label
			if (scale < 0.20)
				return null;

			if (scale != 1.0) {
				// System.out.println("scale = "+scale);
				AffineTransform sTransform = new AffineTransform();
				sTransform.scale(scale, scale);
				lShape = sTransform.createTransformedShape(lShape);
			}
		}

		// System.out.println("  Text size = ("+textWidth+","+textHeight+")");

		double pointX = position.getX();
		double pointY = position.getY();

		double textStartX = pointX;
		double textStartY = pointY;

		switch (tAlign) {
		case ALIGN_CENTER_TOP:
			// System.out.println("  Align = CENTER_TOP");
			textStartX = pointX - textWidth/2;
			textStartY = pointY + textHeight/2;
			break;
		case ALIGN_CENTER_BOTTOM:
			// System.out.println("  Align = CENTER_BOTTOM");
			textStartX = pointX - textWidth/2;
			textStartY = pointY - textHeight;
			break;
		case ALIGN_RIGHT:
			// System.out.println("  Align = RIGHT");
			textStartX = pointX - textWidth;
			textStartY = pointY - textHeight/2;
			break;
		case ALIGN_LEFT:
			// System.out.println("  Align = LEFT");
			textStartX = pointX;
			textStartY = pointY - textHeight/2;
			break;
		case ALIGN_MIDDLE:
			// System.out.println("  Align = MIDDLE");
			textStartX = pointX - textWidth/2;
			textStartY = pointY - textHeight/2;
			break;
		case ALIGN_CENTER:
			// System.out.println("  Align = CENTER");
			textStartX = pointX - textWidth/2;
			break;
		default:
			// System.out.println("  Align = "+tAlign);
		}

		// System.out.println("  Text bounds = "+lShape.getBounds2D());
		// System.out.println("  Position = "+position);

		// System.out.println("  Offset = ("+textStartX+","+textStartY+")");

		// Use the bounding box to create an Affine transform.  We may need to scale the font
		// shape if things are too cramped, but not beneath some arbitrary minimum
		AffineTransform trans = new AffineTransform();
		if (rotation != 0.0)
			trans.rotate(Math.toRadians(rotation), pointX, pointY);
		trans.translate(textStartX, textStartY);

		// System.out.println("  Transform: "+trans);
		return trans.createTransformedShape(lShape);
	}

	/**
 	 * This is used to draw a line from a text box to an object -- for example from a pie label to
 	 * the pie slice itself.
 	 */
	public static Shape getLabelLine(Rectangle2D textBounds, Point2D labelPosition, TextAlignment tAlign) {

		Point2D start = getLabelLineStart(textBounds, tAlign);

		BasicStroke stroke = new BasicStroke(0.5f);
		return stroke.createStrokedShape(new Line2D.Double(start.getX(), start.getY(), labelPosition.getX(), labelPosition.getY()));
	}

	public static Point2D getLabelLineStart(Rectangle2D textBounds, TextAlignment tAlign) {
		double lineStartX = 0;
		double lineStartY = 0;
		switch (tAlign) {
			case ALIGN_CENTER_TOP:
				lineStartY = textBounds.getMinY()-1;
				lineStartX = textBounds.getCenterX();
			break;
			case ALIGN_CENTER_BOTTOM:
				lineStartY = textBounds.getMaxY()+1;
				lineStartX = textBounds.getCenterX();
			break;
			case ALIGN_RIGHT:
				lineStartY = textBounds.getCenterY();
				lineStartX = textBounds.getMaxX()+1;
			break;
			case ALIGN_LEFT:
				lineStartY = textBounds.getCenterY();
				lineStartX = textBounds.getMinX()-1;
			break;
		}
		return new Point2D.Double(lineStartX, lineStartY);
	}

	public static Point2D positionAdjust(Rectangle2D bbox, Rectangle2D textBox, 
	                                     Object pos, Object anchor) {
		if (pos == null)
			return null;

		Point2D anchorOffset = new Point2D.Double(0.0, 0.0);
		if (anchor != null) {
			anchorOffset = anchorOffset(textBox, anchor);
		}

		double x = bbox.getX();
		double y = bbox.getY();
		double nodeWidth = bbox.getWidth();
		double nodeHeight = bbox.getHeight();

		if (pos instanceof Position) {
			Position p = (Position) pos;

			switch (p) {
			case EAST:
				x = nodeWidth/2;
				break;
			case WEST:
				x = -nodeWidth/2;
				break;
			case NORTH:
				y = -nodeHeight/2;
				break;
			case SOUTH:
				y = nodeHeight/2;
				break;
			case NORTHEAST:
				x = nodeWidth/2;
				y = -nodeHeight/2;
				break;
			case NORTHWEST:
				x = -nodeWidth/2;
				y = -nodeHeight/2;
				break;
			case SOUTHEAST:
				x = nodeWidth/2;
				y = nodeHeight/2;
				break;
			case SOUTHWEST:
				x = -nodeWidth/2;
				y = nodeHeight/2;
				break;
			case CENTER:
			default:
			}
		} else if (pos instanceof Point2D.Double) {
			x += ((Point2D.Double)pos).getX();
			y += ((Point2D.Double)pos).getY();
		}

		return new Point2D.Double(x+anchorOffset.getX(), y+anchorOffset.getY());
	}

	public static Point2D anchorOffset(Rectangle2D textBox, Object anchor) {
		double x = textBox.getX();
		double y = textBox.getY();
		double textWidth = textBox.getWidth();
		double textHeight = textBox.getHeight();

		if (anchor instanceof Position) {
			Position p = (Position) anchor;

			switch (p) {
			case EAST:
				x -= textWidth/2;
				break;
			case WEST:
				x += textWidth/2;
				break;
			case NORTH:
				y += textHeight/2;
				break;
			case SOUTH:
				y -= textHeight/2;
				break;
			case NORTHEAST:
				x -= textWidth/2;
				y += textHeight/2;
				break;
			case NORTHWEST:
				x += textWidth/2;
				y += textHeight/2;
				break;
			case SOUTHEAST:
				x -= textWidth/2;
				y -= textHeight/2;
				break;
			case SOUTHWEST:
				x += textWidth/2;
				y -= textHeight/2;
				break;
			case CENTER:
			default:
			}
		} else if (anchor instanceof Point2D.Double) {
			x += ((Point2D.Double)anchor).getX();
			y += ((Point2D.Double)anchor).getY();
		}
		return new Point2D.Double(x,y);
	}

	public static Shape copyShape(Shape shape) {
		AffineTransform identity = new AffineTransform();
		return identity.createTransformedShape(shape);
	}

	public static BufferedImage getShadow(Shape shape, int size) {
		Rectangle2D shapeBounds = shape.getBounds2D();
//		System.out.println("Shape bounds = "+shapeBounds);

		BufferedImage image = new BufferedImage((int)shapeBounds.getWidth(),
		                                        (int)shapeBounds.getHeight(),
		                                        BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.translate(1+(int)(shapeBounds.getWidth()/2),1+(int)(shapeBounds.getHeight()/2));
		g2d.setColor(Color.BLACK);
		// g2d.draw(shape);
		g2d.fill(shape);
		g2d.dispose();
		return image;
		// return createDropShadow(image, size);
	}

	public static BufferedImage createDropShadow(BufferedImage image, int size) {
		BufferedImage shadow = new BufferedImage(image.getWidth() + 4 * size,
		                                         image.getHeight() + 4 * size,
		                                         BufferedImage.TYPE_INT_ARGB);

		Graphics2D g2 = shadow.createGraphics();
		g2.drawImage(image, size * 2, size * 2, null);

		g2.setComposite(AlphaComposite.SrcIn);
		g2.setColor(Color.BLACK);
		g2.fillRect(0, 0, shadow.getWidth(), shadow.getHeight()); 

		g2.dispose();

		shadow = getGaussianBlurFilter(size, true).filter(shadow, null);
		shadow = getGaussianBlurFilter(size, false).filter(shadow, null);

		return shadow;
	}

	public static ConvolveOp getGaussianBlurFilter(int radius, boolean horizontal) 
	{
		if (radius < 1) {
			throw new IllegalArgumentException("Radius must be >= 1");
		}

		int size = radius * 2 + 1;
		float[] data = new float[size];

		float sigma = radius / 3.0f;
		float twoSigmaSquare = 2.0f * sigma * sigma;
		float sigmaRoot = (float) Math.sqrt(twoSigmaSquare * Math.PI);
		float total = 0.0f;

		for (int i = -radius; i <= radius; i++) {
			float distance = i * i;
			int index = i + radius;
			data[index] = (float) Math.exp(-distance / twoSigmaSquare) / sigmaRoot;
			total += data[index];
		}

		for (int i = 0; i < data.length; i++) {
			data[i] /= total;
		}

		Kernel kernel = null;
		if (horizontal) {
			kernel = new Kernel(size, 1, data);
		} else {
			kernel = new Kernel(1, size, data);
		}
		return new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
	}

	public static Shape createPossiblyTransformedShape(AffineTransform xform, Shape shape, boolean rescale) {
		double[] matrix = new double[6];
		xform.getMatrix(matrix);

		// Make sure the scale factors are equal (no weird stretched text!)
		if (rescale) {
			double scale = matrix[0];
			if (matrix[0] != matrix[3]) {
				scale = Math.min(matrix[0], matrix[3]);
			}
			matrix[0] = scale;
			matrix[3] = scale;
		} else {
			matrix[0] = 1.0;
			matrix[3] = 1.0;
		}

		AffineTransform newXform = new AffineTransform(matrix);

		return newXform.createTransformedShape(shape);
	}
}
