package dev.jbang.eclipse.core.internal;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import dev.jbang.eclipse.core.JBangCorePlugin;
import dev.jbang.eclipse.core.internal.classpath.ClasspathContainerSaveHelper;
import dev.jbang.eclipse.core.internal.classpath.IClasspathManager;

public class JBangClasspathUtils {
	
	private static final File STATE_LOCATION_DIR =  JBangCorePlugin.getDefault().getStateLocation().toFile();

	private JBangClasspathUtils() {
		//No public constructor
	}

	public static boolean isJBangClasspathContainer(IPath containerPath) {
		return containerPath != null && containerPath.segmentCount() > 0
				&& IClasspathManager.JBANG_CLASSPATH_CONTAINER_ID.equals(containerPath.segment(0));
	}


	public static IClasspathEntry getJBangContainerEntry(IJavaProject javaProject) {
		if (javaProject != null) {
			try {
				for (IClasspathEntry entry : javaProject.getRawClasspath()) {
					if (JBangClasspathUtils.isJBangClasspathContainer(entry.getPath())) {
						return entry;
					}
				}
			} catch (JavaModelException ex) {
				return null;
			}
		}
		return null;
	}
	
	public static IClasspathEntry getDefaultContainerEntry() {
		    return JavaCore.newContainerEntry(new Path(IClasspathManager.JBANG_CLASSPATH_CONTAINER_ID));
	}

	public static IClasspathContainer getJBangClasspathContainer(IJavaProject project) throws JavaModelException {
		IClasspathEntry[] entries = project.getRawClasspath();
		for (IClasspathEntry entry : entries) {
			if (entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER
					&& JBangClasspathUtils.isJBangClasspathContainer(entry.getPath())) {
				return JavaCore.getClasspathContainer(entry.getPath(), project);
			}
		}
		return null;
	}
	
	public static void saveContainerState(IProject project, IClasspathContainer container) {
	    File containerStateFile = getContainerStateFile(project);
	    try (FileOutputStream is = new FileOutputStream(containerStateFile)) {
	      new ClasspathContainerSaveHelper().writeContainer(container, is);
	    } catch(IOException ex) {
	      JBangCorePlugin.log("Can't save JBang classpath container state for " + project.getName(), ex); //$NON-NLS-1$
	    }
	  }

	
	public static IClasspathContainer getSavedContainer(IProject project) throws CoreException {
	    File containerStateFile = getContainerStateFile(project);
	    if(!containerStateFile.exists()) {
	      return null;
	    }

	    try (FileInputStream is = new FileInputStream(containerStateFile)) {
	      return new ClasspathContainerSaveHelper().readContainer(is);
	    } catch(IOException | ClassNotFoundException ex) {
	      throw new CoreException(new Status(IStatus.ERROR, JBangConstants.PLUGIN_ID, -1, //
	          "Can't read JBang classpath container state for " + project.getName(), ex));
	    }
	  }
	  
	  static File getContainerStateFile(IProject project) {
		    return new File(STATE_LOCATION_DIR, project.getName() + ".container"); //$NON-NLS-1$
	  }

}
