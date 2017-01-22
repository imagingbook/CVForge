
import java.util.Enumeration;
import java.util.Properties;

import cvforge.CVForge;
import cvforge.CVForgeFrame;
import cvforge.CVForgeLauncher;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.ImageProcessor;

/**
 * Launcher for running/ testing in IDE.
 */
public class Main2{
	public static void main(String[] args){
		Class<?> clazz = CVForgeLauncher.class;
		String url = clazz.getResource("/" + clazz.getName().replace('.', '/') + ".class").toString();
		String pluginsDir = url.substring(5, url.length() - clazz.getName().length() - 6);
		System.setProperty("plugins.dir", pluginsDir);
		
		System.out.println("pluginsDir = " + pluginsDir);
		
		new ImageJ();		

		String imagePath = "http://imagej.net/images/clown.jpg";
		ImagePlus image = IJ.openImage(imagePath);
		image.show();
		
		ImageProcessor ip = image.getProcessor(); 

		// run the plugin
		IJ.runPlugIn(clazz.getName(), "");
	}
}
