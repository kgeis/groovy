/*
 * $Id$
 *
 * Copyright 2003 (C) James Strachan and Bob Mcwhirter. All Rights Reserved.
 *
 * Redistribution and use of this software and associated documentation
 * ("Software"), with or without modification, are permitted provided that the
 * following conditions are met:
 *  1. Redistributions of source code must retain copyright statements and
 * notices. Redistributions must also contain a copy of this document.
 *  2. Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *  3. The name "groovy" must not be used to endorse or promote products
 * derived from this Software without prior written permission of The Codehaus.
 * For written permission, please contact info@codehaus.org.
 *  4. Products derived from this Software may not be called "groovy" nor may
 * "groovy" appear in their names without prior written permission of The
 * Codehaus. "groovy" is a registered trademark of The Codehaus.
 *  5. Due credit should be given to The Codehaus - http://groovy.codehaus.org/
 *
 * THIS SOFTWARE IS PROVIDED BY THE CODEHAUS AND CONTRIBUTORS ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE CODEHAUS OR ITS CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 *
 */

/**
 * @TODO: multi threaded compiling of the same class but with different roots
 * for compilation... T1 compiles A, which uses B, T2 compiles B... mark A and B
 * as parsed and then synchronize compilation. Problems: How to synchronize? 
 * How to get error messages?   
 * 
 */
package groovy.lang;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

/**
 * A ClassLoader which can load Groovy classes. The loaded classes are cached, 
 * classes from other classlaoders should not be cached. To be able to load a 
 * script that was asked for earlier but was created later it is essential not
 * to keep anything like a "class not found" information for that class name. 
 * This includes possible parent loaders. Classes that are not chached are always 
 * reloaded.
 *
 * @author <a href="mailto:james@coredevelopers.net">James Strachan</a>
 * @author Guillaume Laforge
 * @author Steve Goetze
 * @author Bing Ran
 * @author <a href="mailto:scottstirling@rcn.com">Scott Stirling</a>
 * @author <a href="mailto:blackdrag@gmx.org">Jochen Theodorou</a>
 * @version $Revision$
 */
public class GroovyClassLoader extends URLClassLoader {

    /**
     * this cache contains the loaded classes or PARSING, if the class is currently parsed 
     */
    protected Map classCache = new HashMap();
    protected Map sourceCache = new HashMap();
    private CompilerConfiguration config;
    private Boolean recompile = null; 
    
    private GroovyResourceLoader resourceLoader = new GroovyResourceLoader() {
        public URL loadGroovySource(final String filename) throws MalformedURLException {
            URL file = (URL) AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    return  getSourceFile(filename);
                }
            });
            return file;
        }
    };

    /**
     * creates a GroovyClassLoader using the current Thread's context
     * Class loader as parent.
     */
    public GroovyClassLoader() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * creates a GroovyClassLoader using the given ClassLoader as parent
     */
    public GroovyClassLoader(ClassLoader loader) {
        this(loader, null);
    }

    /**
     * creates a GroovyClassLoader using the given GroovyClassLoader as parent.
     * This loader will get the parent's CompilerConfiguration
     */
    public GroovyClassLoader(GroovyClassLoader parent) {
        this(parent, parent.config, false);
    }

    /**
     * creates a GroovyClassLaoder.
     * @param parent the parten class loader
     * @param config the compiler configuration
     * @param useConfigurationClasspath determines if the configurations classpath should be added 
     */
    public GroovyClassLoader(ClassLoader parent, CompilerConfiguration config, boolean useConfigurationClasspath) {
        super(new URL[0],parent);
        if (config==null) config = CompilerConfiguration.DEFAULT;
        this.config = config;
        if (useConfigurationClasspath) {
            for (Iterator it=config.getClasspath().iterator(); it.hasNext();) {
                String path = (String) it.next();
                this.addClasspath(path);
            }
        }
    }
    
    /**
     * creates a GroovyClassLoader using the given ClassLoader as parent.
     */
    public GroovyClassLoader(ClassLoader loader, CompilerConfiguration config) {
        this(loader,config,true);
    }
    
    public void setResourceLoader(GroovyResourceLoader resourceLoader) {
        if (resourceLoader == null) {
            throw new IllegalArgumentException("Resource loader must not be null!");
        }
        this.resourceLoader = resourceLoader;
    }

    public GroovyResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    /**
     * Loads the given class node returning the implementation Class
     *
     * @param classNode
     * @return a class
     */
    public Class defineClass(ClassNode classNode, String file) {
        //return defineClass(classNode, file, "/groovy/defineClass");
        throw new DeprecationException("the method GroovyClassLoader#defineClass(ClassNode, String) is no longer used and removed");
    }

    /**
     * Loads the given class node returning the implementation Class. 
     * 
     * WARNING: this compilation is not synchronized
     *
     * @param classNode
     * @return a class
     */
    public Class defineClass(ClassNode classNode, String file, String newCodeBase) {
        CodeSource codeSource = null;
        try {
            codeSource = new CodeSource(new URL("file", "", newCodeBase), (java.security.cert.Certificate[]) null);
        } catch (MalformedURLException e) {
            //swallow
        }

        CompilationUnit unit = createCompilationUnit(config,codeSource);
        ClassCollector collector = createCollector(unit,classNode.getModule().getContext());
        try {
            unit.addClassNode(classNode);
            unit.setClassgenCallback(collector);
            unit.compile(Phases.CLASS_GENERATION);

            return collector.generatedClass;
        } catch (CompilationFailedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses the given file into a Java class capable of being run
     *
     * @param file the file name to parse
     * @return the main class defined in the given script
     */
    public Class parseClass(File file) throws CompilationFailedException, IOException {
        return parseClass(new GroovyCodeSource(file));
    }

    /**
     * Parses the given text into a Java class capable of being run
     *
     * @param text     the text of the script/class to parse
     * @param fileName the file name to use as the name of the class
     * @return the main class defined in the given script
     */
    public Class parseClass(String text, String fileName) throws CompilationFailedException {
        return parseClass(new ByteArrayInputStream(text.getBytes()), fileName);
    }

    /**
     * Parses the given text into a Java class capable of being run
     *
     * @param text the text of the script/class to parse
     * @return the main class defined in the given script
     */
    public Class parseClass(String text) throws CompilationFailedException {
        return parseClass(new ByteArrayInputStream(text.getBytes()), "script" + System.currentTimeMillis() + ".groovy");
    }

    /**
     * Parses the given character stream into a Java class capable of being run
     *
     * @param in an InputStream
     * @return the main class defined in the given script
     */
    public Class parseClass(InputStream in) throws CompilationFailedException {
        return parseClass(in, "script" + System.currentTimeMillis() + ".groovy");
    }

    public Class parseClass(final InputStream in, final String fileName) throws CompilationFailedException {
        // For generic input streams, provide a catch-all codebase of
        // GroovyScript
        // Security for these classes can be administered via policy grants with
        // a codebase of file:groovy.script
        GroovyCodeSource gcs = (GroovyCodeSource) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return new GroovyCodeSource(in, fileName, "/groovy/script");
            }
        });
        return parseClass(gcs);
    }


    public Class parseClass(GroovyCodeSource codeSource) throws CompilationFailedException {
        return parseClass(codeSource, true);
    }

    /**
     * Parses the given code source into a Java class. If there is a class file
     * for the given code source, then no parsing is done, instead the cached class is returned.
     * 
     * @param shouldCacheSource if true then the generated class will be stored in the source cache 
     *
     * @return the main class defined in the given script
     */
    public Class parseClass(GroovyCodeSource codeSource, boolean shouldCacheSource) throws CompilationFailedException {
        synchronized (classCache) {
            Class answer = (Class) sourceCache.get(codeSource.getName());
            if (answer!=null) return answer;
            
            // Was neither already loaded nor compiling, so compile and add to
            // cache.
            try {
                CompilationUnit unit = createCompilationUnit(config, codeSource.getCodeSource());
                SourceUnit su = null;
                if (codeSource.getFile()==null) {
                    su = unit.addSource(codeSource.getName(), codeSource.getInputStream());
                } else {
                    su = unit.addSource(codeSource.getFile());
                }
                
                ClassCollector collector = createCollector(unit,su);
                unit.setClassgenCallback(collector);
                int goalPhase = Phases.CLASS_GENERATION;
                if (config != null && config.getTargetDirectory()!=null) goalPhase = Phases.OUTPUT;
                unit.compile(goalPhase);
                
                answer = collector.generatedClass;
                for (Iterator iter = collector.getLoadedClasses().iterator(); iter.hasNext();) {
                    Class clazz = (Class) iter.next();
                    setClassCacheEntry(clazz);
                }
                if (shouldCacheSource) sourceCache.put(codeSource.getName(), answer);
            } finally {
                try {
                    codeSource.getInputStream().close();
                } catch (IOException e) {
                    throw new GroovyRuntimeException("unable to close stream",e);
                }
            }
            return answer;
        }
    }
    
    /**
     * gets the currently used classpath. 
     * @return a String[] containing the file information of the urls 
     * @see #getURLs()
     */
    protected String[] getClassPath() {
        //workaround for Groovy-835
        URL[] urls = getURLs();
        String[] ret = new String[urls.length];
        for (int i = 0; i < ret.length; i++) {
            ret[i] =  urls[i].getFile();
        }
        return ret;
    }

    /**
     * expands the classpath
     * @param pathList an empty list that will contain the elements of the classpath
     * @param classpath the classpath specified as a single string
     * @deprecated
     */
    protected void expandClassPath(List pathList, String base, String classpath, boolean isManifestClasspath) {
        throw new DeprecationException("the method groovy.lang.GroovyClassLoader#expandClassPath(List,String,String,boolean) is no longer used internally and removed");
    }

    /**
     * A helper method to allow bytecode to be loaded. spg changed name to
     * defineClass to make it more consistent with other ClassLoader methods
     * @deprecated
     */
    protected Class defineClass(String name, byte[] bytecode, ProtectionDomain domain) {
        throw new DeprecationException("the method groovy.lang.GroovyClassLoader#defineClass(String,byte[],ProtectionDomain) is no longer used internally and removed");
    }
    
    public static class InnerLoader extends GroovyClassLoader{
        private GroovyClassLoader delegate;
    	public InnerLoader(GroovyClassLoader delegate) {
    		super(delegate);
            this.delegate = delegate;
    	}
        public void addClasspath(String path) {
            delegate.addClasspath(path);
        }
        public void clearCache() {
            delegate.clearCache();
        }
        public URL findResource(String name) {
            return delegate.findResource(name);
        }
        public Enumeration findResources(String name) throws IOException {
            return delegate.findResources(name);
        }
        public Class[] getLoadedClasses() {
            return delegate.getLoadedClasses();
        }
        public URL getResource(String name) {
            return delegate.getResource(name);
        }
        public InputStream getResourceAsStream(String name) {
            return delegate.getResourceAsStream(name);
        }
        public GroovyResourceLoader getResourceLoader() {
            return delegate.getResourceLoader();
        }
        public URL[] getURLs() {
            return delegate.getURLs();
        }
        public Class loadClass(String name, boolean lookupScriptFiles, boolean preferClassOverScript, boolean resolve) throws ClassNotFoundException, CompilationFailedException {
            Class c = findLoadedClass(name);
            if (c!=null) return c;
            return delegate.loadClass(name, lookupScriptFiles, preferClassOverScript, resolve);
        }
        public Class parseClass(GroovyCodeSource codeSource, boolean shouldCache) throws CompilationFailedException {
            return delegate.parseClass(codeSource, shouldCache);
        }
        public void setResourceLoader(GroovyResourceLoader resourceLoader) {
            delegate.setResourceLoader(resourceLoader);
        }
        public void addURL(URL url) {
            delegate.addURL(url);
        }        
    }
    
    /**
     * creates a new CompilationUnit. If you want to add additional
     * phase operations to the CompilationUnit (for example to inject
     * additional methods, variables, fields), then you should overwrite
     * this method.
     * 
     * @param config the compiler configuration, usually the same as for this class loader
     * @param source the source containing the initial file to compile, more files may follow during compilation
     * 
     * @return the CompilationUnit
     */
    protected CompilationUnit createCompilationUnit(CompilerConfiguration config, CodeSource source) {
        return new CompilationUnit(config, source, this);
    }

    /**
     * creates a ClassCollector for a new compilation.
     * @param unit the compilationUnit
     * @param su  the SoruceUnit
     * @return the ClassCollector
     */
    protected ClassCollector createCollector(CompilationUnit unit,SourceUnit su) {
    	InnerLoader loader = (InnerLoader) AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                return new InnerLoader(GroovyClassLoader.this);
            }
        }); 
        return new ClassCollector(loader, unit, su);
    }

    public static class ClassCollector extends CompilationUnit.ClassgenCallback {
        private Class generatedClass;
        private GroovyClassLoader cl;
        private SourceUnit su;
        private CompilationUnit unit;
        private Collection loadedClasses = null;

        protected ClassCollector(InnerLoader cl, CompilationUnit unit, SourceUnit su) {
            this.cl = cl;
            this.unit = unit;
            this.loadedClasses = new ArrayList();
            this.su = su;
        }

        protected Class onClassNode(ClassWriter classWriter, ClassNode classNode) {
            byte[] code = classWriter.toByteArray();

            Class theClass = cl.defineClass(classNode.getName(), code, 0, code.length, unit.getAST().getCodeSource());
            cl.resolveClass(theClass);
            this.loadedClasses.add(theClass);

            if (generatedClass == null) {
                ModuleNode mn = classNode.getModule();
                SourceUnit msu = null;
                if (mn!=null) msu = mn.getContext();
                ClassNode main = null;
                if (mn!=null) main = (ClassNode) mn.getClasses().get(0);
                if (msu==su && main==classNode) generatedClass = theClass;
            }

            return theClass;
        }

        public void call(ClassVisitor classWriter, ClassNode classNode) {
            onClassNode((ClassWriter) classWriter, classNode);
        }

        public Collection getLoadedClasses() {
            return this.loadedClasses;
        }
    }

    /**
     * open up the super class define that takes raw bytes
     *
     */
    public Class defineClass(String name, byte[] b) {
        return super.defineClass(name, b, 0, b.length);
    }
    
    /**
     * loads a class from a file or a parent classloader.
     * This method does call loadClass(String, boolean, boolean, boolean)
     * with the last parameter set to false.
     * @throws CompilationFailedException 
     */
    public Class loadClass(final String name, boolean lookupScriptFiles, boolean preferClassOverScript)
        throws ClassNotFoundException, CompilationFailedException
    {
        return loadClass(name,lookupScriptFiles,preferClassOverScript,false);
    }

    /**
     * gets a class from the class cache. This cache contains only classes loaded through
     * this class loader or an InnerLoader instance. If no class is stored for a
     * specific name, then the method should return null. 
     *  
     * @param name of the class
     * @return the class stored for the given name 
     * @see #removeClassCacheEntry(String)
     * @see #setClassCacheEntry(Class)
     * @see #clearCache()
     */    
    protected Class getClassCacheEntry(String name) {
        if (name==null) return null;
        synchronized (classCache) {
            Class cls = (Class) classCache.get(name);
            return cls;
        }
    }
    
    /**
     * sets an entry in the class cache. 
     * @param cls the class
     * @see #removeClassCacheEntry(String)
     * @see #getClassCacheEntry(String)
     * @see #clearCache()
     */
    protected void setClassCacheEntry(Class cls) {
        synchronized (classCache) {
            classCache.put(cls.getName(),cls);
        }
    }    
    
    /**
     * removes a class from the class cache.
     * @param name of the class
     * @see #getClassCacheEntry(String)
     * @see #setClassCacheEntry(Class)
     * @see #clearCache()
     */
    protected void removeClassCacheEntry(String name) {
        synchronized (classCache) {
            classCache.remove(name);
        }
    }    
    
    /**
     * adds a URL to the classloader.
     * @param url the new classpath element 
     */
    public void addURL(URL url) {
        super.addURL(url);
    }
    
    /**
     * Indicates if a class is recompilable. Recompileable means, that the classloader
     * will try to locate a groovy source file for this class and then compile it again,
     * adding the resulting class as entry to the cache. Giving null as class is like a
     * recompilation, so the method should always return true here. Only classes that are
     * implementing GroovyObject are compileable and only if the timestamp in the class
     * is lower than Long.MAX_VALUE.  
     * 
     *  NOTE: First the parent loaders will be asked and only if they don't return a
     *  class the recompilation will happen. Recompilation also only happen if the source
     *  file is newer.
     * 
     * @see #isSourceNewer(URL, Class)
     * @param cls the class to be tested. If null the method should return true
     * @return true if the class should be compiled again
     */
    protected boolean isRecompilable(Class cls) {
        if (cls==null) return true;
        if (recompile==null && !config.getRecompileGroovySource()) return false;
        if (recompile!=null && !recompile.booleanValue()) return false;
        if (!GroovyObject.class.isAssignableFrom(cls)) return false;
        long timestamp = getTimeStamp(cls); 
        if (timestamp == Long.MAX_VALUE) return false;
        
        return true;
    }
    
    /**
     * sets if the recompilation should be enable. There are 3 possible
     * values for this. Any value different than null overrides the
     * value from the compiler configuration. true means to recompile if needed
     * false means to never recompile.  
     * @param mode the recompilation mode
     * @see CompilerConfiguration
     */
    public void setShouldRecompile(Boolean mode){
        recompile = mode;
    }
    
    
    /**
     * gets the currently set recompilation mode. null means, the 
     * compiler configuration is used. False means no recompilation and 
     * true means that recompilation will be done if needed. 
     * @return the recompilation mode
     */
    public Boolean isShouldRecompile(){
        return recompile;
    }

    /**
     * loads a class from a file or a parent classloader.
     *
     * @param name                      of the class to be loaded
     * @param lookupScriptFiles         if false no lookup at files is done at all
     * @param preferClassOverScript     if true the file lookup is only done if there is no class
     * @param resolve                   @see ClassLoader#loadClass(java.lang.String, boolean)
     * @return                          the class found or the class created from a file lookup
     * @throws ClassNotFoundException
     */
    public Class loadClass(final String name, boolean lookupScriptFiles, boolean preferClassOverScript, boolean resolve)
        throws ClassNotFoundException, CompilationFailedException
    {
        // look into cache
        Class cls=getClassCacheEntry(name);
        
        // enable recompilation?
        boolean recompile = isRecompilable(cls);
        if (!recompile) return cls;

        // check security manager
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            String className = name.replace('/', '.');
            int i = className.lastIndexOf('.');
            if (i != -1) {
                sm.checkPackageAccess(className.substring(0, i));
            }
        }

        // try parent loader
        ClassNotFoundException last = null;
        try {
            Class parentClassLoaderClass = super.loadClass(name, resolve);
            // always return if the parent loader was successfull 
            if (cls!=parentClassLoaderClass) return parentClassLoaderClass;
        } catch (ClassNotFoundException cnfe) {
            last = cnfe;
        } catch (NoClassDefFoundError ncdfe) {
            if (ncdfe.getMessage().indexOf("wrong name")>0) {
                last = new ClassNotFoundException(name);
            } else {
                throw ncdfe;
            }
        }

        if (cls!=null) {
            // prefer class if no recompilation
            preferClassOverScript |= !recompile;
            if (preferClassOverScript) return cls;
        }

        // at this point the loading from a parent loader failed
        // and we want to recompile if needed.
        if (lookupScriptFiles) {
            // synchronize on cache, as we want only one compilation
            // at the same time
            synchronized (classCache) {
                // try groovy file
                try {
                    // check if recompilation already happend.
                    if (getClassCacheEntry(name)!=cls) return getClassCacheEntry(name);
                    URL source = resourceLoader.loadGroovySource(name);
                    cls = recompile(source,name,cls);
                } catch (IOException ioe) {
                    last = new ClassNotFoundException("IOException while openening groovy source: " + name, ioe);
                } finally {
                    if (cls==null) {
                        removeClassCacheEntry(name);
                    } else {
                        setClassCacheEntry(cls);
                    }
                }
            }
        }

        if (cls==null) {
            // no class found, there has to be an exception before then
            if (last==null) throw new AssertionError(true);
            throw last;
        }
        return cls;
    }

    /**
     * (Re)Comipiles the given source. 
     * This method starts the compilation of a given source, if
     * the source has changed since the class was created. For
     * this isSourceNewer is called.
     * 
     * @see #isSourceNewer(URL, Class)
     * @param source the source pointer for the compilation
     * @param className the name of the class to be generated
     * @param oldClass a possible former class
     * @return the old class if the source wasn't new enough, the new class else
     * @throws CompilationFailedException if the compilation failed
     * @throws IOException if the source is not readable
     * 
     */
    protected Class recompile(URL source, String className, Class oldClass) throws CompilationFailedException, IOException {
        if (source != null) {
            // found a source, compile it if newer
            if ((oldClass!=null && isSourceNewer(source, oldClass)) || (oldClass==null)) {
                sourceCache.remove(className);
                return parseClass(source.openStream(),className);
            }
        }
        return oldClass;
    }

    /**
     * Implemented here to check package access prior to returning an
     * already loaded class.
     * @throws CompilationFailedException if the compilation failed
     * @throws ClassNotFoundException if the class was not found
     * @see java.lang.ClassLoader#loadClass(java.lang.String, boolean)
     */
    protected Class loadClass(final String name, boolean resolve) throws ClassNotFoundException {
        return loadClass(name,true,false,resolve);
    }

    /**
     * gets the time stamp of a given class. For groovy
     * generated classes this usually means to return the value
     * of the static field __timeStamp. If the parameter doesn't
     * have such a field, then Long.MAX_VALUE is returned
     * 
     * @param cls the class 
     * @return the time stamp
     */
    protected long getTimeStamp(Class cls) {
        Long o;
        try {
            Field field = cls.getField(Verifier.__TIMESTAMP);
            o = (Long) field.get(null);
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
        return o.longValue();
    }

    private URL getSourceFile(String name) {
        String filename = name.replace('.', '/') + config.getDefaultScriptExtension();
        URL ret = getResource(filename);
        if (ret!=null && ret.getProtocol().equals("file")) {
            String fileWithoutPackage = filename;
            if (fileWithoutPackage.indexOf('/')!=-1){
                int index = fileWithoutPackage.lastIndexOf('/');
                fileWithoutPackage = fileWithoutPackage.substring(index+1);
            }
            File path = new File(ret.getFile()).getParentFile();
            if (path.exists() && path.isDirectory()) {
                File file = new File(path, fileWithoutPackage);
                if (file.exists()) {
                    // file.exists() might be case insensitive. Let's do
                    // case sensitive match for the filename
                    File parent = file.getParentFile();
                    String[] files = parent.list();
                    for (int j = 0; j < files.length; j++) {
                        if (files[j].equals(fileWithoutPackage)) return ret;
                    }
                }
            }
            //file does not exist!
            return null;
        }
        return ret;
    }

    /**
     * Decides if the given source is newer than a class.
     * 
     * @see #getTimeStamp(Class)
     * @param source the source we may want to compile
     * @param cls the former class
     * @return true if the source is newer, false else
     * @throws IOException if it is not possible to open an
     * connection for the given source
     */
    protected boolean isSourceNewer(URL source, Class cls) throws IOException {
        long lastMod;

        // Special handling for file:// protocol, as getLastModified() often reports
        // incorrect results (-1)
        if (source.getProtocol().equals("file")) {
            // Coerce the file URL to a File
            String path = source.getPath().replace('/', File.separatorChar).replace('|', ':');
            File file = new File(path);
            lastMod = file.lastModified();
        }
        else {
            lastMod = source.openConnection().getLastModified();
        }
        long classTime = getTimeStamp(cls);
        return classTime+config.getMinimumRecompilationIntervall() < lastMod;
    }

    /**
     * adds a classpath to this classloader.  
     * @param path is a jar file or a directory.
     * @see #addURL(URL)
     */
    public void addClasspath(final String path) {
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                try {
                    File f = new File(path);
                    URL newURL = f.toURI().toURL();
                    URL[] urls = getURLs();
                    for (int i=0; i<urls.length; i++) {
                        if (urls[i].equals(newURL)) return null;
                    }
                    addURL(newURL);
                } catch (MalformedURLException e) {
                    //TODO: fail through ?
                }
                return null;
            }
        });
    }

    /**
     * <p>Returns all Groovy classes loaded by this class loader.
     *
     * @return all classes loaded by this class loader
     */
    public Class[] getLoadedClasses() {
        synchronized (classCache) {
            return (Class[]) classCache.values().toArray(new Class[0]);
        }
    }
    
    /**
     * removes all classes from the class cache.
     * @see #getClassCacheEntry(String)
     * @see #setClassCacheEntry(Class)
     * @see #removeClassCacheEntry(String)
     */    
    public void clearCache() {
        synchronized (classCache) {
            classCache.clear();
        }
    }
}
