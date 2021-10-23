/*-
 * #%L
 * IO plugin for Fiji.
 * %%
 * Copyright (C) 2008 - 2021 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package sc.fiji.io;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.plugin.PlugIn;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;

import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.DocumentLoader;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.gvt.renderer.StaticRenderer;
import org.w3c.dom.svg.SVGDocument;
import org.w3c.dom.svg.SVGSVGElement;

public class SVG_Reader extends ImagePlus implements PlugIn {

	/** Expects path as argument, or will ask for it and then open the image.*/
	public void run(final String arg) {
		File file = null;
		if (arg != null && arg.length() > 0)
			file = new File(arg);
		else {
			OpenDialog od =
				new OpenDialog("Choose .svg file", null);
			String directory = od.getDirectory();
			if (null == directory)
				return;
			file = new File(directory + "/" + od.getFileName());
		}

		UserAgentAdapter userAgent = new UserAgentAdapter();
		StaticRenderer renderer = new StaticRenderer();
		DocumentLoader loader = new DocumentLoader(userAgent);
		BridgeContext context =
			new BridgeContext(userAgent, loader);
		userAgent.setBridgeContext(context);
		SVGDocument document;
		try {
			document = (SVGDocument)
				loader.loadDocument(file.toURI().toString());
		} catch (IOException e) {
			IJ.error("Could not open " + file.toURI());
			return;
		}
		GVTBuilder builder = new GVTBuilder();
		renderer.setTree(builder.build(context, document));
		SVGSVGElement root = ((SVGDocument)document).getRootElement();
		float svgX = root.getX().getBaseVal().getValue();
		float svgY = root.getY().getBaseVal().getValue();
		float svgWidth = root.getWidth().getBaseVal().getValue();
		float svgHeight = root.getHeight().getBaseVal().getValue();

		GenericDialog gd = new GenericDialog("SVG dimensions");
		gd.addNumericField("width", svgWidth, 0);
		gd.addNumericField("height", svgHeight, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		int w = (int)gd.getNextNumber();
		int h = (int)gd.getNextNumber();

		AffineTransform transform = new AffineTransform();
		transform.translate(-svgX, -svgY);
		transform.scale(w / svgWidth, h / svgHeight);
		renderer.setTransform(transform);
		renderer.updateOffScreen(w, h);
		Rectangle r = new Rectangle(0, 0, w, h);
		renderer.repaint(r);

		setTitle(file.getName());
		setImage(renderer.getOffScreen());

		if (arg.equals(""))
			show();
	}
}
