package cvforge;

import reflectiontools.JarInspector;

import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;

import ij.IJ;

import java.io.CharArrayWriter;
//import java.awt.Point;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.*;


public class CVForge {
	
	public static final String SEP = System.getProperty("file.separator");
	
    public static final String VERSION = "CVForge v0.2 (beta)";
    public static final String CONFIGFILE = "cvforge.config";              // location of config file
    public static final String PLUGINDIR = System.getProperty("user.dir") + SEP + "plugins" + SEP;
	public static final String BITS = System.getProperty("sun.arch.data.model");		
	
	public static CVForgeClassLoader FORGELOADER;

	
	//protected Point defaultFramePos = new Point(0, 0);
	//protected Point defaultFrameSize = new Point(0,0);
    protected boolean verbose = true;
    protected String libPath = null;                                                // local path to active lib
    protected ArrayList<String> libsAvailable; 		                                // libs available for loading

    // buffered variables
    protected JTree libTree;   												// class and method tree for loaded lib
    protected HashMap<String, Class> classCache;							// mapping of strings to classes
    protected HashMap<String, Method> methodCache;                          // mapping of strings to methods
    protected HashMap<String, String> config;                               // config map

    //protected ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
    protected CVForgeClassLoader forgeLoader;
    
    /**
     * Call initialization.
     */
    public CVForge(){
        init();
    }

    /**
     * Initialization by loading config, finding jars, generating cache.
     */
    public void init(){
        loadConfig(CONFIGFILE);
        forgeLoader = new CVForgeClassLoader();
        //hijackIJClassLoader();
        
        libsAvailable = new ArrayList<String>();
        String[] foundJars = CVInstaller.getInstalledOpenCV();
        for(String found: foundJars)
        	libsAvailable.add(found);
        
        if((libPath == null) && (!libsAvailable.isEmpty())){
    		libPath = libsAvailable.get(0);
        }     
    }

    /**
     * Load config file
     * @param path
     */
    protected void loadConfig(String path){
        config = ConfigIO.loadConfig(path);
        
        // get and convert config values
        String verboseConfig = config.get("verbose");
        verboseConfig = (verboseConfig == null)? "true" : verboseConfig;
        verbose = Boolean.parseBoolean(verboseConfig);
        libPath = config.get("libPath");
        libsAvailable = new ArrayList<String>();
        for(Map.Entry<String,String> entry: config.entrySet()){
            if(entry.getKey().contains("installed-")) {
                libsAvailable.add(entry.getValue());
            }
        }
    }
    
    /**
     * Exchange ImageJ ClassLoader by custom CVForgeClassLoader
     */
    public void hijackIJClassLoader(){
		Class ijclass = IJ.class;
		try{
			Method loaderSetter = ijclass.getDeclaredMethod("setClassLoader", new Class[]{ClassLoader.class});
			loaderSetter.setAccessible(true);
			loaderSetter.invoke(null, forgeLoader);
		}catch(Exception e){IJ.showMessage("hijack failed");};
    }

    /**
     * Return the internal ClassLoader.
     * Use with caution, as modifications can potentially break IJ. 
     * @return Internal ClassLoader.
     */
    public CVForgeClassLoader getClassLoader(){
    	return forgeLoader;
    }
    
    /**
     * Loads the OpenCV jar identified by the argument.
     * Generate library tree and method cache on-the-fly.
     * @see generateMethodCache()
     * @see generateLibraryTree()
     * @param version Library version/ path to load.
     */
    public void loadOpenCV(String version) throws Exception{ 
    	//System.setProperty("plugin.dir", version);
    	classCache = new HashMap<String, Class>();
        methodCache = new HashMap<String, Method>();        
    	if((version != null)/* && libsAvailable.contains(version)*/){
    		String bits = (BITS.equals("64"))? "64": "86"; 
    		String dllName = new File(version).getName().replace("opencv-", "opencv_java").replace(".jar", ".dll");
    		System.load(PLUGINDIR + "x" + bits + SEP + dllName);
        	libPath = PLUGINDIR + version;
            config.put("libPath", version);
            methodCache = JarInspector.generateMethodCache(libPath, forgeLoader);
            classCache = JarInspector.generateConstructableClassCache(libPath, forgeLoader);
        
            List<Class> classes = JarInspector.loadClassesFromJar(libPath, forgeLoader);
        	for(Class c: classes){
        		IJ.register(c);
        	}
    	}
    	
    	generateLibraryTree();
    	if(!methodCache.isEmpty()){
    		Executer.initCVForgeExecuter(libPath, forgeLoader);
    	}
    }

    /**
     * Generate a JTree representation of the library based on the methodCache;
     */
    protected void generateLibraryTree(){
        libTree = new JTree(new DefaultMutableTreeNode("No library loaded"));
        if(!methodCache.isEmpty()){        	
        	try{
        		libTree = LibTreeBuilder.generateLibTree(libPath);
    		}catch(Exception e){
        		IJ.beep();
    			IJ.showStatus(e.toString());
				CharArrayWriter caw = new CharArrayWriter();
				PrintWriter pw = new PrintWriter(caw);
				e.printStackTrace(pw);
				IJ.log(caw.toString());
        	}
        }
    }

    /**
     * Install and remember OpenCV jar.
     * The path to the jar will be stored in the config file once the plugin saves.
     * @see CVInstaller.installOpenCV()
     * @param path Path to OpenCV jar.
     */
    public void installOpenCV(String path){
        if(CVInstaller.installOpenCV(path, forgeLoader)){
        	String relPath = PLUGINDIR + path.substring(path.lastIndexOf(SEP)+1, path.length());
	        config.put("libPath", relPath);
	        
	        if(!libsAvailable.contains(relPath))
	        	libsAvailable.add(relPath);
        }else{
        	System.out.println("installation of jar failed: " + path);
        }
    }

    /**
     * Dump config map in file defined by CONFIGPATH.
     */
    public void saveSettings(){
        ConfigIO.writeConfig(config, CONFIGFILE);
    }

    /**
     * Get name of currently loaded OpenCV lib.
     * Returns null, if none loaded/ available.
     * @return local path to currently loaded library, null else.
     */
    public String activeLib(){
        return libPath;
    }
    
    /**
     * Get list of installed libraries.
     * @return Paths to known libraries. 
     */
    public ArrayList<String> availableLibs(){
    	return libsAvailable;
    }

    /**
     * Tree representation of loaded library and its methods.
     * @return Generated JTree, granted that a library has been loaded.
     */
    public JTree getLibraryTree(){
        return libTree;
    }

    /**
     * Mapping of mapping name to method.
     * @return Generated cache of methods, granted a library has been loaded.
     */
    public HashMap<String, Method> getMethodCache(){
        return methodCache;
    }
    
    public HashMap<String, Class> getClassCache(){
    	return classCache;
    }

    public void setVerbose(boolean v){
    	config.put("verbose", Boolean.toString(v));
    	verbose = v;
    }
    
    public boolean isVerbose(){
    	return verbose;
    }
}
