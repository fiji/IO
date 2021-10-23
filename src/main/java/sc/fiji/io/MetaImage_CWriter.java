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

/**
    MetaImage writer plugin for ImageJ.

    This plugin writes MetaImage text-based tagged format files.

    Author: Kang Li (kangli AT cs.cmu.edu)

    Installation:
      Download MetaImage_Reader_Writer.jar to the plugins folder, or subfolder.
      Restart ImageJ, and there will be new File/Import/MetaImage... and
      File/Save As/MetaImage... commands.

    History:
      2007/04/07: First version

    References:
      MetaIO Documentation (http://www.itk.org/Wiki/MetaIO/Documentation)
*/

/**
Copyright (C) 2007-2008 Kang Li. All rights reserved.

Permission to use, copy, modify, and distribute this software for any purpose
without fee is hereby granted,  provided that this entire notice  is included
in all copies of any software which is or includes a copy or modification  of
this software  and in  all copies  of the  supporting documentation  for such
software. Any for profit use of this software is expressly forbidden  without
first obtaining the explicit consent of the author.

THIS  SOFTWARE IS  BEING PROVIDED  "AS IS",  WITHOUT ANY  EXPRESS OR  IMPLIED
WARRANTY.  IN PARTICULAR,  THE AUTHOR  DOES NOT  MAKE ANY  REPRESENTATION OR
WARRANTY OF ANY KIND CONCERNING  THE MERCHANTABILITY OF THIS SOFTWARE  OR ITS
FITNESS FOR ANY PARTICULAR PURPOSE.
*/

////extionsions by Roman Grothausmann:
//01: implemented writing zlib compressed MHDs (i.e. .zraw)
//02: implemented saving data locally, i.e. MHAs; ByteOrder fitting ITK
//02b: added dummy header entry "CompressedDataSize = 9999999999999" with which ITK only reports a warning
//02c: MHD/MHA-bug resoved

//todo
// -add CompressedDataSize to header (without ITK aborts on decompression)
//  seems this can only be done after DeflaterOutputStream has written all bytes
//  however, the line for CompressedDataSize has to be inserted into the header
//  this can be done writing a new file or by insertion
//  extending a file in java is cumbersum so replacing a dummy string instead might be best for MHAs
//  i.e. write a header with CompressedDataSize = abcdefgUVWXYZ
//  and after DeflaterOutputStream has finished replace abcdefgUVWXYZ by # of written bytes

import java.io.*;
import java.awt.*;
import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.plugin.*;
import ij.process.*;
import ij.measure.Calibration;

import java.util.zip.DeflaterOutputStream;

//  This plugin saves MetaImage format files.
//  It appends the '.mhd' and '.raw' suffixes to the header and data files, respectively.
//


////from: http://imagej.nih.gov/ij/developer/source/ij/io/FileSaver.java.html
class DeflateExtendedFileSaver extends FileSaver {

    private ImagePlus mimp;
    private FileInfo mfi;


    public DeflateExtendedFileSaver (ImagePlus imp) {
        super(imp);
        this.mimp = imp;
        mfi = imp.getFileInfo();

    }
   
    void showErrorMessage(IOException e) {
        String msg = e.getMessage();
        if (msg.length()>100)
            msg = msg.substring(0, 100);
        error("An error occured writing the file.\n \n" + msg);
    }
    
    private void error(String msg) {
        IJ.error("FileSaver", msg);
    }


        /** Save the image as raw data using the specified path. */
    public boolean saveAsRaw(String path) {
        mfi.nImages = 1;
        mfi.intelByteOrder = Prefs.intelByteOrder;
        boolean signed16Bit = false;
        short[] pixels = null;
        int n = 0;
        try {
            signed16Bit = mimp.getCalibration().isSigned16Bit();
            if (signed16Bit) {
                pixels = (short[])mimp.getProcessor().getPixels();
                n = mimp.getWidth()*mimp.getHeight();
                for (int i=0; i<n; i++)
                    pixels[i] = (short)(pixels[i]-32768);
                }
	    OutputStream out;
	    if(path.endsWith(".mha"))//append
		out= new DeflaterOutputStream(new FileOutputStream(path, true));
	    else
		out= new DeflaterOutputStream(new FileOutputStream(path));
            ImageWriter file = new ImageWriter(mfi);
            file.write(out);
            out.close();
            }
        catch (IOException e) {
            showErrorMessage(e);
            return false;
            }
        if (signed16Bit) {
            for (int i=0; i<n; i++)
                pixels[i] = (short)(pixels[i]+32768);
            }
        //updateImp(fi, fi.RAW);
        //updateImp(mfi, mfi.COMPRESSION_UNKNOWN);
        return true;
        }
   

    /** Save the stack as raw data using the specified path. */
    public boolean saveAsRawStack(String path) {
        if (mfi.nImages==1)
            {IJ.error("This is not a stack"); return false;}
        mfi.intelByteOrder = Prefs.intelByteOrder;
        boolean signed16Bit = false;
        Object[] stack = null;
        int n = 0;
        boolean virtualStack = mimp.getStackSize()>1 && mimp.getStack().isVirtual();
        if (virtualStack) {
            mfi.virtualStack = (VirtualStack)mimp.getStack();
            if (mimp.getProperty("AnalyzeFormat")!=null) mfi.fileName="FlipTheseImages";
            }
        try {
            signed16Bit = mimp.getCalibration().isSigned16Bit();
            if (signed16Bit && !virtualStack) {
                stack = (Object[])mfi.pixels;
                n = mimp.getWidth()*mimp.getHeight();
                for (int slice=0; slice<mfi.nImages; slice++) {
                    short[] pixels = (short[])stack[slice];
                    for (int i=0; i<n; i++)
                        pixels[i] = (short)(pixels[i]-32768);
                    }
                }
	    OutputStream out;
	    if(path.endsWith(".mha"))//append
		out= new DeflaterOutputStream(new FileOutputStream(path, true));
	    else
		out= new DeflaterOutputStream(new FileOutputStream(path));
            ImageWriter file = new ImageWriter(mfi);
            file.write(out);
            out.close();
            }
        catch (IOException e) {
            showErrorMessage(e);
            return false;
            }
        if (signed16Bit) {
            for (int slice=0; slice<mfi.nImages; slice++) {
                short[] pixels = (short[])stack[slice];
                for (int i=0; i<n; i++)
                    pixels[i] = (short)(pixels[i]+32768);
                }
            }
        //updateImp(mfi, mfi.COMPRESSION_UNKNOWN);
        return true;
        }

    }


public final class MetaImage_CWriter implements PlugIn {

    public void run(String arg) {
        ImagePlus imp = WindowManager.getCurrentImage();
        if (imp == null) {
            IJ.noImage();
            return;
        }
        if (imp.getCalibration().isSigned16Bit() && IJ.versionLessThan("1.34e")) {
            IJ.error("MetaImage Reader: Please upgrade to ImageJ v1.34e or later.");
            return;
        }
        String dir = "", baseName = "";
        if (arg == null || arg.length() == 0) {
            SaveDialog sd = new SaveDialog(
                "Save as compressed MetaImage", imp.getTitle(), "");
            dir = sd.getDirectory();
            baseName = sd.getFileName();
        }
        else {
            File file = new File(arg);
            if (file.isDirectory()) {
                dir = arg;
                baseName = imp.getTitle();
            }
            else {
                dir = file.getParent();
                baseName = file.getName();
            }
        }

        if (baseName == null || baseName.length() == 0)
            return;

        save(imp, dir, baseName);
        IJ.showStatus(baseName + " saved");
    }


    private void save(ImagePlus imp, String dir, String baseName) {

	String headerName;
	String dataName;

        String lowerBaseName = baseName.toLowerCase();
        if (lowerBaseName.endsWith(".mha")){
	    headerName = baseName;
	    dataName = baseName;
	}
	else if (lowerBaseName.endsWith(".mhd")){
	    baseName= baseName.substring(0, baseName.length() - 4);
	    headerName = baseName + ".mhd";
	    dataName = baseName + ".raw";
	}
	else {
	    headerName = baseName + ".mha";
	    dataName = baseName + ".mha";
	}

        if (!dir.endsWith(File.separator) && dir.length() > 0)
            dir += File.separator;

        try {
            IJ.showStatus("Saving " + headerName + "...");
            if (writeHeader(imp, dir + headerName, dataName)) {
                // Save data file.
                IJ.showStatus("Writing " + dataName + "...");
                if (imp.getStackSize() > 1)
                    new DeflateExtendedFileSaver(imp).saveAsRawStack(dir + dataName);
                else
                    new DeflateExtendedFileSaver(imp).saveAsRaw(dir + dataName);
            }
        }
        catch (IOException e) {
            IJ.error("MetaImage_Writer: " + e.getMessage());
        }
    }


    private boolean writeHeader(ImagePlus imp, String path, String dataFile)
        throws IOException
    {
        FileInfo fi = imp.getFileInfo();
        String numChannels = "1", type = "MET_NONE";

        switch (fi.fileType) {
        case FileInfo.COLOR8:          type = "MET_UCHAR";  break;
        case FileInfo.GRAY8:           type = "MET_UCHAR";  break;
        case FileInfo.GRAY16_SIGNED:   type = "MET_SHORT";  break;
        case FileInfo.GRAY16_UNSIGNED: type = "MET_USHORT"; break;
        case FileInfo.GRAY32_INT:      type = "MET_INT";    break;
        case FileInfo.GRAY32_UNSIGNED: type = "MET_UINT";   break;
        case FileInfo.GRAY32_FLOAT:    type = "MET_FLOAT";  break;
        case FileInfo.RGB:
            type = "MET_UCHAR_ARRAY";  numChannels = "3";
            break;
        case FileInfo.RGB48:
            type = "MET_USHORT_ARRAY"; numChannels = "3";
            break;
        default:
            throw new IOException(
                "Unsupported data format.");
        }

        FileOutputStream file = new FileOutputStream(path);
        PrintStream stream = new PrintStream(file);
        
        int ndims = (imp.getStackSize() > 1) ? 3 : 2;

        stream.println("ObjectType = Image");
        if (ndims == 3)
            stream.println("NDims = 3");
        else
            stream.println("NDims = 2");
        stream.println("BinaryData = True");

        if (fi.intelByteOrder)
            stream.println("BinaryDataByteOrderMSB = False");
	else
            stream.println("BinaryDataByteOrderMSB = True");
	    
        stream.println("CompressedData = True");
        stream.println("CompressedDataSize = 9999999999999"); 

        if (ndims == 3) {
            stream.println("DimSize = " + fi.width + " " + fi.height + " " + fi.nImages);
            stream.println("ElementSize = " + fi.pixelWidth + " " + fi.pixelHeight + " " + fi.pixelDepth);
        }
        else {
            stream.println("DimSize = " + fi.width + " " + fi.height);
            stream.println("ElementSize = " + fi.pixelWidth + " " + fi.pixelHeight);
        }
        if (numChannels != "1")
            stream.println("ElementNumberOfChannels = " + numChannels);
        stream.println("ElementType = " + type);


        if (dataFile.endsWith(".mha"))
	    stream.println("ElementDataFile = LOCAL");
	else
	    stream.println("ElementDataFile = " + dataFile);

        stream.close();
        file.close();

        return true;
    }
}
