/*-
 * #%L
 * IO plugin for Fiji.
 * %%
 * Copyright (C) 2008 - 2024 Fiji developers.
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
/**
 * Copyright Albert Cardona 2008.
 * Released under the General Public License in its latest version.
 *
 * Modeled after scripts/pdf-extract-images.py by Johannes Schindelin
 */

package sc.fiji.io;

import ij.IJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

import java.awt.image.BufferedImage;

import org.jpedal.PdfDecoder;
import org.jpedal.objects.PdfImageData;

/** Extract all images from a PDF file (or from an URL given as argument),
 *  and open them all within ImageJ in their original resolution.
*/
public class Extract_Images_From_PDF implements PlugIn {
	public void run(String arg) {

		final String path = PDF_Viewer.getPath(arg);
		if (null == path) return;
		PdfDecoder decoder = null;

		try {
			decoder = new PdfDecoder(false);
			decoder.setExtractionMode(PdfDecoder.RAWIMAGES | PdfDecoder.FINALIMAGES);
			if (path.startsWith("http://")) decoder.openPdfFileFromURL(path);
			else decoder.openPdfFile(path);

			final int page_count = decoder.getPageCount();

			for (int page=1; page<=page_count; page++) {
				IJ.showStatus("Decoding page " + page);
				decoder.decodePage(page);
				final PdfImageData images = decoder.getPdfImageData();
				final int image_count = images.getImageCount();

				for (int i=0; i<image_count; i++) {
					IJ.showStatus("Opening image " + i + "/" + image_count + " from page " + page + "/" + page_count);
					String name = images.getImageName(i);
					BufferedImage image = decoder.getObjectStore().loadStoredImage("R" + name);
					new ImagePlus(name, image).show();
				}
			}
			IJ.showStatus("Done.");
		} catch (Exception e) {
			IJ.log("Error: " + e);
			e.printStackTrace();
		} finally {
			decoder.flushObjectValues(true);
			decoder.closePdfFile();
		}
	}
}
