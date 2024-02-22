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
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.Menus;
import ij.plugin.PlugIn;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.zip.GZIPInputStream;

import org.scijava.Context;
import org.scijava.io.IOService;
import org.scijava.ui.UIService;

// Plugin to handle file types which are not implemented
// directly in ImageJ through io.Opener
// NB: since there is no _ in the name it will not appear in Plugins menu
// -----
// Can be user modified so that your own specialised file types
// can be opened through File ... Open
// OR by drag and drop onto the ImageJ main panel
// OR by double clicking in the MacOS 9/X Finder
// -----
// Go to the point marked MODIFY HERE and modify to
// recognise and load your own file type
// -----
// Gregory Jefferis - 030629
// jefferis@stanford.edu

/**
 * Plugin to handle file types which are not implemented directly in ImageJ
 * through io.Opener.
 */
public class HandleExtraFileTypes extends ImagePlus implements PlugIn {

	static final int IMAGE_OPENED = -1;
	static final int PLUGIN_NOT_FOUND = -2;
	static final boolean LOCI_PRESENT = checkForLoci();

	private static boolean checkForLoci() {
		// This should run without exception in headless mode
		boolean lociPresent = true;
		try {
			lociPresent =
				IJ.getClassLoader().loadClass("loci.plugins.LociImporter") != null;
		}
		catch (final ClassNotFoundException e) {
			lociPresent = false;
		}
		if (IJ.debugMode) {
			IJ.log("HEFT: loci is" + (lociPresent ? " " : " not ") + "present");
		}
		return lociPresent;
	}

	/** Called from io/Opener.java. */
	@Override
	public void run(final String path) {

		if (path.equals("")) return;
		final File theFile = new File(path);
		final String fileName = theFile.getName();
		String directory = theFile.getParent();
		if (directory == null) directory = "";
		else {
			directory = directory.replace('\\', '/');
			if (!directory.endsWith("/")) directory += "/";
		}

		// Try and recognise file type and load the file if recognised
		final ImagePlus imp = openImage(directory, fileName, path);
		if (imp == null) {
			// failed to load file or plugin has opened and displayed it
			IJ.showStatus("");
			return; // failed to load file or plugin has opened and displayed it
		}
		final ImageStack stack = imp.getStack();
		// fetch the title from the stack (falling back to the fileName)
		final String title = imp.getTitle().equals("") ? fileName : imp.getTitle();
		// set the stack of this HandleExtraFileTypes object
		// to that attached to the ImagePlus object returned by openImage()
		setStack(title, stack);
		// copy over calibration info since it doesn't come with the ImageProcessor
		setCalibration(imp.getCalibration());
		// also copy the Show Info field over if it exists
		if (imp.getProperty("Info") != null) {
			setProperty("Info", imp.getProperty("Info"));
		}
		// also copy the subtitle ("Label") field over if it exists
		if (imp.getProperty("Label") != null) {
			setProperty("Label", imp.getProperty("Label"));
		}
		// copy over the FileInfo
		setFileInfo(imp.getOriginalFileInfo());
		// copy dimensions
		if (IJ.getVersion().compareTo("1.38s") >= 0) {
			setDimensions(imp.getNChannels(), imp.getNSlices(), imp.getNFrames());
		}
		if (IJ.getVersion().compareTo("1.41o") >= 0) {
			setOpenAsHyperStack(imp.getOpenAsHyperStack());
		}
	}

	private Object
		tryOpen(final String directory, String name, final String path)
	{
		// set up a stream to read in 132 bytes from the file header
		// These can be checked for "magic" values which are diagnostic
		// of some image types
		InputStream is;
		final byte[] buf = new byte[132];
		try {
			if (0 == path.indexOf("http://")) is = new URL(path).openStream();
			else is = new FileInputStream(path);
			is.read(buf, 0, 132);
			is.close();
		}
		catch (final IOException e) {
			// couldn't open the file for reading
			return null;
		}
		name = name.toLowerCase();
		width = PLUGIN_NOT_FOUND;

		// Temporarily suppress "plugin not found" errors if LOCI Bio-Formats plugin
		// is installed
		if (IJ.getVersion().compareTo("1.37u") >= 0 && LOCI_PRESENT) {
			IJ.suppressPluginNotFoundError();
		}

		// OK now we get to the interesting bit

		// GJ: added Biorad PIC confocal file handler
		// ------------------------------------------
		// These make 12345 if you read them as the right kind of short
		// and should have this value in every Biorad PIC file
		if (name.endsWith(".pic.gz") || buf[54] == 57 && buf[55] == 48) {
			return tryPlugIn("Biorad_Reader", path);
		}
		// GJ: added Gatan Digital Micrograph DM3 handler
		// ----------------------------------------------
		// check if the file ends in .DM3 or .dm3,
		// and bytes make an int value of 3 which is the DM3 version number
		if (name.endsWith(".dm3") && buf[0] == 0 && buf[1] == 0 && buf[2] == 0 &&
			buf[3] == 3)
		{
			return tryPlugIn("sc.fiji.io.DM3_Reader", path);
		}

		// IPLab file handler
		// Little-endian IPLab files start with "iiii" or "mmmm".
		if (name.endsWith(".ipl") ||
			(buf[0] == 105 && buf[1] == 105 && buf[2] == 105 && buf[3] == 105) ||
			(buf[0] == 109 && buf[1] == 109 && buf[2] == 109 && buf[3] == 109))
		{
			return tryPlugIn("sc.fiji.io.IPLab_Reader", path);
		}

		// Packard InstantImager format (.img) handler -> check HERE
		// before Analyze check below!
		// Check extension and signature bytes KAJ_
		if (name.endsWith(".img") && buf[0] == 75 && buf[1] == 65 && buf[2] == 74 &&
			buf[3] == 0)
		{
			return tryPlugIn("InstantImager_Reader", path);
		}

		// Analyze format (.img/.hdr) handler
		// Opens the file using the Nifti_Reader if it is installed,
		// otherwise the Analyze_Reader is used. Note that
		// the Analyze_Reader plugin opens and displays the
		// image and does not implement the ImagePlus class.
		if (name.endsWith(".img") || name.endsWith(".hdr")) {
			if (Menus.getCommands().get("NIfTI-Analyze") != null) {
				return tryPlugIn("Nifti_Reader", path);
			}
			return tryPlugIn("Analyze_Reader", path);
		}

		// NIFTI format (.nii) handler
		if (name.endsWith(".nii") || name.endsWith(".nii.gz") ||
			name.endsWith(".nii.z"))
		{
			return tryPlugIn("Nifti_Reader", path);
		}

		// Image Cytometry Standard (.ics) handler
		// http://valelab.ucsf.edu/~nico/IJplugins/Ics_Opener.html
		if (name.endsWith(".ics")) {
			return tryPlugIn("Ics_Opener", path);
		}

		// Princeton Instruments SPE image file (.spe) handler
		// http://rsb.info.nih.gov/ij/plugins/spe.html
		if (name.endsWith(".spe")) {
			return tryPlugIn("OpenSPE_", path);
		}

		// Zeiss Confocal LSM 510 image file (.lsm) handler
		// http://rsb.info.nih.gov/ij/plugins/lsm-reader.html
		if (name.endsWith(".lsm")) {
			Object obj = tryPlugIn("LSM_Reader", path);
			if (obj == null && Menus.getCommands().get("Show LSMToolbox") != null) {
				obj = tryPlugIn("LSM_Toolbox", "file=" + path);
			}
			return obj;
		}

		// BM: added Bruker file handler 29.07.04
		if (name.equals("ser") || name.equals("fid") || name.equals("2rr") ||
			name.equals("2ii") || name.equals("3rrr") || name.equals("3iii") ||
			name.equals("2dseq"))
		{
			IJ.showStatus("Opening Bruker " + name + " File");
			return tryPlugIn("BrukerOpener", name + "|" + path);
		}

		// AVI: open AVI files using AVI_Reader plugin
		if (name.endsWith(".avi")) {
			return tryPlugIn("AVI_Reader", path);
		}

		// QuickTime: open .mov and .pict files using QT_Movie_Opener plugin
		if (name.endsWith(".mov") || name.endsWith(".pict")) {
			return tryPlugIn("QT_Movie_Opener", path);
		}

		// ZVI file handler
		// Little-endian ZVI and Thumbs.db files start with d0 cf 11 e0
		// so we can only look at the extension.
		if (name.endsWith(".zvi")) {
			return tryPlugIn("ZVI_Reader", path);
		}

		// University of North Carolina (UNC) file format handler
		// 'magic' numbers are (int) offsets to data structures and
		// may change in future releases.
		if (name.endsWith(".unc") ||
			(buf[3] == 117 && buf[7] == -127 && buf[11] == 36 && buf[14] == 32 && buf[15] == -127))
		{
			return tryPlugIn("UNC_Reader", path);
		}

		// Albert Cardona: read .mrc files (little endian). Documentation at:
		// http://ami.scripps.edu/prtl_data/mrc_specification.htm
		// The parsing of the header is a bare minimum of what could be done.
		if (name.endsWith(".mrc") || name.endsWith(".rec") ||
			name.endsWith(".st") || name.endsWith(".tmg"))
		{
			return tryPlugIn("sc.fiji.io.Open_MRC_Leginon", path);
		}

		// Deltavision file handler
		// Open DV files generated on Applied Precision DeltaVision systems
		if (name.endsWith(".dv") || name.endsWith(".r3d")) {
			return tryPlugIn("Deltavision_Opener", path);
		}

		// Albert Cardona: read .dat files from the EMMENU software
		// 'new format' only
		if (name.endsWith(".dat") && 1 == buf[1] && 0 == buf[2]) {
			return tryPlugIn("sc.fiji.io.Open_DAT_EMMENU", path);
		}

		// Albert Cardona: read TrakEM2 .xml files
		if (name.endsWith(".xml") || name.endsWith(".xml.gz")) {
			byte[] b = buf;
			if (name.endsWith("z")) {
				GZIPInputStream gz = null;
				b = new byte[132];
				try {
					gz =
						new GZIPInputStream(new BufferedInputStream(new FileInputStream(
							path)));
					gz.read(b, 0, 132);
				}
				catch (final Exception gze) {
					gze.printStackTrace();
					return null;
				}
				finally {
					try {
						gz.close();
					}
					catch (final IOException gzioe) {
						gzioe.printStackTrace();
						return null;
					}
				}
			}
			if (-1 != new String(b).toLowerCase().indexOf("trakem2")) {
				try {
					// portable way, resists absence of TrakEM2_.jar in the classpath
					final Class<?> cla = Class.forName("ini.trakem2.Project");
					if (null != cla) {
						final Method method = cla.getMethod("openFSProject", String.class);
						method.invoke(null, path);
					}
					// assume success in any case
					width = IMAGE_OPENED;
				}
				catch (final Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		}

		// Stephan Saalfeld: read .df3 files. Documentation at:
		// http://www.povray.org/documentation/view/3.6.1/374/
		if (name.endsWith(".df3")) {
			return tryPlugIn("sc.fiji.io.Open_DF3", path);
		}

		if (name.endsWith(".dat") && buf[0] == -45 && buf[1] == -19 &&
			buf[2] == -11 && buf[3] == -14)
		{
			return tryPlugIn("sc.fiji.io.FIBSEM_Reader", path);
		}

		// Albert Cardona: open one or more pages of a PDF file as images
		if (name.endsWith(".pdf")) {
			return tryPlugIn("sc.fiji.io.PDF_Viewer", path);
		}

		// Greg Jefferis: open nrrd images
		// see Nrrd_Reader code or http://teem.sourceforge.net/nrrd/
		try {
			final String nrrdMagic = new String(buf, 0, 7, "US-ASCII");
			if (nrrdMagic.equals("NRRD000")) {
				// Ok we've identified the file type - now load it
				return tryPlugIn("sc.fiji.io.Nrrd_Reader", path);
			}
		}
		catch (final Exception e) {}
		// Greg Jefferis added Torsten Rohlfing binary file handler
		// ----------------------------------------------
		// Check if the file ends in .bin or in the case
		// of the gzip compressed version .bin.gz
		if (name.toLowerCase().endsWith(".bin") ||
			name.toLowerCase().endsWith(".bin.gz"))
		{
			// Since those filenames are not very specific, do a bit more checking
			// These files come in pairs as follows:
			// T1_SABB4flip01_warp_m0g40c4e1e-1x16r3/image.bin.gz
			// T1_SABB4flip01_warp_m0g40c4e1e-1x16r3.study/images
			String dirWithoutSeparator = directory;
			if (directory.endsWith(File.separator)) {
				dirWithoutSeparator = directory.substring(0, directory.length() - 1);
			}
			final File studyDir = new File(dirWithoutSeparator + ".study");

			if (studyDir.isDirectory())
			// Ok we've identified the file type - now load it
			return tryPlugIn("sc.fiji.io.TorstenRaw_GZ_Reader", path);
		}

		// Johannes Schindelin: open one or more images in a .ico file
		if (name.endsWith(".ico")) return tryPlugIn("sc.fiji.io.ICO_Reader", path);

		// Johannes Schindelin: open one or more images in a .icns file
		if (name.endsWith(".icns")) return tryPlugIn("sc.fiji.io.Icns_Reader", path);

		// Johannes Schindelin: render an .svg image into an ImagePlus
		if (name.endsWith(".svg")) return tryPlugIn("sc.fiji.io.SVG_Reader", path);

		// Johannes Schindelin: open an LSS16 (SYSLINUX) image
		if (name.endsWith(".lss")) return tryPlugIn("sc.fiji.io.LSS16_Reader", path);

		// Johannes Schindelin: handle scripts
		if (name.endsWith(".py")) {
			return tryPlugIn("Jython.Refresh_Jython_Scripts", path);
		}
		if (name.endsWith(".rb")) {
			return tryPlugIn("JRuby.Refresh_JRuby_Scripts", path);
		}
		if (name.endsWith(".js")) {
			return tryPlugIn("Javascript.Refresh_Javascript_Scripts", path);
		}
		if (name.endsWith(".clj")) {
			return tryPlugIn("Clojure.Refresh_Clojure_Scripts", path);
		}
		if (name.endsWith(".bs") || name.endsWith(".bsh")) {
			return tryPlugIn("BSH.Refresh_BSH_Scripts", path);
		}

		// Larry Lindsey: Convert a Reconstruct .ser file into a TrakEM2 .xml file
		if (name.endsWith(".ser")) {
			return tryPlugIn(
				"edu.utexas.clm.reconstructreader.reconstruct.Reconstruct_Reader", path);
		}

		// Timo Rantalainen and Michael Doube: read Stratec pQCT files.
		// File naming convention is IDDDDDDD.MHH, where D is decimal and H is hex
		if (name.matches("[iI]\\d{7}\\.[mM]\\p{XDigit}{2}")) {
			return tryPlugIn("org.doube.bonej.pqct.Read_Stratec_File", path);
		}

		if (name.endsWith(".obj") || name.endsWith(".dxf") || name.endsWith(".stl"))
		{
			return tryPlugIn("ImageJ_3D_Viewer", path);
		}

		// Christopher Bruns: Read V3DRAW files from Vaa3D application
		// Supported formats:
		// * Peng uncompressed format
		// * Murphy pack-bits/difference compressed format
		// Myers variant (.v3draw, .v3dpbd) not yet supported
		try {
			final String vaa3dCookie = new String(buf, 0, 24);
			if (vaa3dCookie.equals("raw_image_stack_by_hpeng") ||
				vaa3dCookie.equals("v3d_volume_pkbitdf_encod"))
			{
				return tryPlugIn("org.janelia.vaa3d.reader.Vaa3d_Reader", path);
			}
		}
		catch (final Exception exc) {}

		// Michael Doube: read Scanco ISQ files
		// File name is ADDDDDDD.ISQ;D where D is a decimal and A is a letter
		try {
			final String isqMagic = new String(buf, 0, 16, "UTF-8");
			if (name.matches("[a-z]\\d{7}.isq;\\d+") ||
				isqMagic.equals("CTDATA-HEADER_V1"))
			{
				return tryPlugIn("org.bonej.io.ISQReader", path);
			}
		}
		catch (final Exception e) {}

		// Larry Lindsey: open Archipelago cluster configuration file
		if (name.endsWith(".arc")) {
			return tryPlugIn("edu.utexas.clm.archipelago.Fiji_Archipelago", path);
		}

		// Roman Grothausmann: read metaimages (ITK) with MetaImage_Reader
		if (name.endsWith(".mhd")) {
			// IJ.log("Found MHD, trying MetaImage_Reader...");
			return tryPlugIn("sc.fiji.io.MetaImage_Reader", path);
		}
		if (name.endsWith(".mha")) {
			// IJ.log("Found MHA, trying MetaImage_Reader...");
			return tryPlugIn("sc.fiji.io.MetaImage_Reader", path);
		}

		// Jerome Parent : open .bin file with Koala_Bin_Reader plugin
		// ----------------------------------------------
		// check if the file ends in .bin
		if (name.endsWith(".bin")) {
			return tryPlugIn("sc.fiji.io.Koala_Bin_Reader", path);
		}

		// Samuel Inverso: open raw files with raw file plugin
		if (name.endsWith(".raw")) {
			return tryPlugIn("ij.plugin.Raw", path);
		}

		// Les Foster: read HHMI/Janelia Research Campus' HDF5 format.
		if (name.endsWith(".h5j")) {
			return tryPlugIn("org.janelia.it.fiji.plugins.h5j.H5j_Reader", path);
		}

		// ****************** MODIFY HERE ******************
		// do what ever you have to do to recognise your own file type
		// and then call appropriate plugin using the above as models
		// e.g.:

		/*
		// A. Dent: Added XYZ handler
		// ----------------------------------------------
		// check if the file ends in .xyz, and bytes 0 and 1 equal 42
		if (name.endsWith(".xyz") && buf[0] == 42 && buf[1] == 42) {
			// Ok we've identified the file type - now load it
			return tryPlugIn("XYZ_Reader", path);
		}
		*/

		return null;
	}

	private ImagePlus openImage(final String directory, final String name,
		final String path)
	{
		final Object o = tryOpen(directory, name, path);
		// if an image was returned, assume success
		if (o instanceof ImagePlus) return (ImagePlus) o;

		// tryPlugIn sets width to IMAGE_OPENED when a plugin that does not
		// extend ImagePlus successfully opens the image
		if (width == IMAGE_OPENED) return null;

		// try opening the file with Bio-Formats plugin - always check this last!
		// Do not call Bio-Formats if File>Import>Image Sequence is being used.
		if (o == null &&
			(IJ.getVersion().compareTo("1.38j") < 0 || !IJ.redirectingErrorMessages()) &&
			(new File(path).exists()))
		{
			try {
				final Object loci = IJ.runPlugIn("loci.plugins.LociImporter", path);
				if (loci != null) {
					// plugin exists and was launched
						// check whether plugin was successful
						final Class<?> c = loci.getClass();
						final boolean success = c.getField("success").getBoolean(loci);
						final boolean canceled = c.getField("canceled").getBoolean(loci);
						if (success || canceled) {
							width = IMAGE_OPENED;
							return null;
						}
					}
				}
			catch (final Exception exc) {
				IJ.log("Error opening the input in LociImporter who says:\n-----\n"
						+ exc.getMessage() + "\n-----");
				if (IJ.debugMode) IJ.handleException(exc);
			}
		}

		// Finally, try opening the file with the SciJava IOService.
		try {
			final Object ctx = IJ.runPlugIn("org.scijava.Context", "");
			if (ctx instanceof Context) {
				final Context context = (Context) ctx;
				final IOService ioService = context.getService(IOService.class);
				final UIService uiService = context.getService(UIService.class);
				if (ioService != null && uiService != null) {
					final Object data = ioService.open(path);
					if (data != null) {
						width = IMAGE_OPENED;
						uiService.show(data);
						return null;
					}
				}
			}
		}
		catch (final IOException exc) {
			// NB: Fail silently. :'-(
		}

		return null;
	}

	/**
	 * Attempts to open the specified path with the given plugin.
	 *
	 * @return A reference to the plugin, if it was successful.
	 */
	private Object tryPlugIn(final String className, final String path) {
		Object o = IJ.runPlugIn(className, path);
		if (o instanceof ImagePlus) {
			// plugin extends ImagePlus class
			final ImagePlus imp = (ImagePlus) o;
			if (imp.getWidth() == 0) o = null; // invalid image
			else width = IMAGE_OPENED; // success
		}
		else if (o != null) {
			// plugin was run but does not extend ImagePlus; assume success
			width = IMAGE_OPENED;
		} // ... else plugin was not run/found
		return o;
	}

}
