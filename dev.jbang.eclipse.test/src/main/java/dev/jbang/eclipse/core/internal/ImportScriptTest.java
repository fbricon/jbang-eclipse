package dev.jbang.eclipse.core.internal;

import static dev.jbang.eclipse.core.internal.utils.ClasspathHelpers.assertJava;
import static dev.jbang.eclipse.core.internal.utils.ImportScriptUtils.importJBangFolder;
import static dev.jbang.eclipse.core.internal.utils.ImportScriptUtils.importJBangScript;
import static dev.jbang.eclipse.core.internal.utils.JobHelpers.waitForJobsToComplete;
import static dev.jbang.eclipse.core.internal.utils.WorkspaceHelpers.assertNoErrors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.apt.core.util.AptConfig;
import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.Test;

import dev.jbang.eclipse.core.internal.utils.WorkspaceHelpers;

public class ImportScriptTest extends AbstractJBangTest {

	@Test
	public void importScript() throws Exception {
		var jbp = importJBangScript("hello.java");
		assertNotNull(jbp);
		IProject project = jbp.getProject();
		assertEquals("hello.java", project.getName());
		waitForJobsToComplete();
		assertNoErrors(project);
		assertJava(project, "11");
	}

	@Test
	public void importBrokenScript() throws Exception {
		var jbp = importJBangScript("brokendeps.java");
		assertNotNull(jbp);
		assertEquals("brokendeps.java", jbp.getProject().getName());
		waitForJobsToComplete();
		var script = jbp.getMainSourceFile();
		var markers = script.findMarkers(JBangConstants.MARKER_ID, true, 0);
		assertEquals(1, markers.length, "Unexpected markers. Received:" + WorkspaceHelpers.toString(markers));
		//Old JBang ( < 0.99.0 ) versions generate an extra marker
		// IMarker jbangError = markers[0];
		// assertEquals(1, jbangError.getAttribute(IMarker.LINE_NUMBER));
		// assertEquals("[jbang] [ERROR] Could not resolve dependencies from mavencentral=https://repo1.maven.org/maven2/", jbangError.getAttribute(IMarker.MESSAGE));
		IMarker jbangDepError = markers[0];
		assertEquals(2, jbangDepError.getAttribute(IMarker.LINE_NUMBER));
		assertEquals("Could not resolve dependency com.github.lalyos:jfiglet:6.6.6", jbangDepError.getAttribute(IMarker.MESSAGE));
	}

	@Test
	public void importScriptWithDependency() throws Exception {
		var jbp = importJBangScript("root.java", "dependency.java");
		assertNotNull(jbp);
		assertEquals("root.java", jbp.getProject().getName());
		waitForJobsToComplete();
		IProject project = jbp.getProject();
		assertNoErrors(project);
		var script = jbp.getMainSourceFile();
		assertEquals("root.java", script.getName());
		var dependency = script.getParent().getFile(new Path("dependency.java"));
		assertTrue(dependency.exists(), dependency.getName() + " doesn't exist");
	}

	@Test
	public void importJBangBuild() throws Exception {
		var jbp = importJBangScript("foo/build.jbang", "foo/foo.java");
		assertNotNull(jbp);
		assertEquals("foo", jbp.getProject().getName());
		waitForJobsToComplete();
		IProject project = jbp.getProject();
		assertNoErrors(project);
		var build = jbp.getMainSourceFile();
		assertEquals("build.jbang", build.getName());
		var foo = build.getParent().getFile(new Path("foo.java"));
		assertTrue(foo.exists(), foo.getName() + " doesn't exist");
	}

	@Test
	public void importFolderAsProject() throws Exception {
		var jbp = importJBangFolder("project-per-folder/");
		assertNotNull(jbp);
		assertEquals("foo.java", jbp.getProject().getName());
		waitForJobsToComplete();
		IProject project = jbp.getProject();
		assertNoErrors(project);
		var foo = jbp.getMainSourceFile();
		assertTrue(foo.exists(), foo.getName() + " doesn't exist");

		var bar = project.getFile("src/bar.java");
		assertTrue(bar.exists(), bar.getName() + " doesn't exist");
	}
	
	@Test
	public void importNestedScript() throws Exception {
		var jbp = importJBangScript("nested/src/main/jbang/foo/bar/hello.java");
		assertNotNull(jbp);
		assertEquals("hello.java", jbp.getProject().getName());
		waitForJobsToComplete();
		IProject project = jbp.getProject();
		assertNoErrors(project);
		var foo = jbp.getMainSourceFile();
		System.err.println(foo);
		assertTrue(jbp.getProject().getFile("src/foo/bar/hello.java").exists(), foo + "not found");
	}

	@Test
	public void importWithSpaces() throws Exception {
		var jbp = importJBangScript("gimme some space/hello.java");
		assertNotNull(jbp);
		assertEquals("hello.java", jbp.getProject().getName());
		waitForJobsToComplete();
		IProject project = jbp.getProject();
		assertNoErrors(project);
		var main = jbp.getMainSourceFile();
		assertEquals("hello.java", main.getName());
	}
	
	@Test
	public void importAnnotationProcessedScript() throws Exception {
		var jbp = importJBangScript("mapstruct.java");
		assertNotNull(jbp);
		assertEquals("mapstruct.java", jbp.getProject().getName());
		waitForJobsToComplete();
		IProject project = jbp.getProject();
		assertNoErrors(project);
		
		var script = jbp.getMainSourceFile();
		assertEquals("mapstruct.java", script.getName());

		var javaProject = JavaCore.create(project);
		assertTrue(AptConfig.isEnabled(javaProject));
		Map<String, String> options = AptConfig.getRawProcessorOptions(javaProject);
		assertEquals("true", options.get("mapstruct.verbose"));
	}
}
