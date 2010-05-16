/*
 *  Copyright 2005, 2006, 2008 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.ibatis.ibator.eclipse.core.merge;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.ibatis.ibator.exception.ShellException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.text.edits.TextEdit;

/**
 * This class handles the task of merging changes into an existing Java file.
 * 
 * This class makes several assumptions about the structure of the new and
 * existing files, including:
 * 
 * <ul>
 *   <li>The imports of both files are fully qualified (no wildcard imports)</li>
 *   <li>The super interfaces of both files are NOT fully qualified</li>
 *   <li>The super classes of both files are NOT fully qualified</li>
 * </ul>
 * 
 * @author Jeff Butler
 */
public class JavaFileMerger {
    
    private String newJavaSource;
    private String existingFilePath;
    private String[] javaDocTags; 

    public JavaFileMerger(String newJavaSource, String existingFilePath,
            String[] javaDocTags) {
        super();
        this.newJavaSource = newJavaSource;
        this.existingFilePath = existingFilePath;
        this.javaDocTags = javaDocTags;
    }

    @SuppressWarnings("unchecked")
    public String getMergedSource() throws ShellException {
        ASTParser astParser = ASTParser.newParser(AST.JLS3);
        NewJavaFileVisitor newJavaFileVisitor = visitNewJavaFile(astParser);

        IFile existingFile = getFile();
        
        ICompilationUnit icu = JavaCore.createCompilationUnitFrom(existingFile);
        IDocument document;
        try {
            document = new Document(icu.getSource());
        } catch (CoreException e) {
            throw new ShellException(e.getStatus().getMessage(), e);
        }

        // delete ibator generated stuff, and collect imports
        ExistingJavaFileVisitor visitor = new ExistingJavaFileVisitor(javaDocTags);

        astParser.setSource(icu);
        CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
        AST ast = cu.getAST();
        cu.recordModifications();
        cu.accept(visitor);

        TypeDeclaration typeDeclaration = visitor.getTypeDeclaration();
        if (typeDeclaration == null) {
            StringBuffer sb = new StringBuffer();
            sb.append("No types defined in the file ");
            sb.append(existingFile.getName());

            throw new ShellException(sb.toString());
        }

        // reconcile the superinterfaces
        List<Type> newSuperInterfaces = getNewSuperInterfaces(typeDeclaration
                .superInterfaceTypes(), newJavaFileVisitor);
        for (Type newSuperInterface : newSuperInterfaces) {
            if (newSuperInterface.isSimpleType()) {
                SimpleType st = (SimpleType) newSuperInterface;
                Name name = ast.newName(st.getName().getFullyQualifiedName());
                SimpleType newSt = ast.newSimpleType(name);
                typeDeclaration.superInterfaceTypes().add(newSt);
            } else {
                // this shouldn't happen - ibator only generates simple names
                throw new ShellException("The Java file merger only supports simple types as super interfaces");
            }
        }

        // set the superclass
        if (newJavaFileVisitor.getSuperclass() != null) {
            if (newJavaFileVisitor.getSuperclass().isSimpleType()) {
                SimpleType st = (SimpleType) newJavaFileVisitor.getSuperclass();
                Name name = ast.newName(st.getName().getFullyQualifiedName());
                SimpleType newSt = ast.newSimpleType(name);
                typeDeclaration.setSuperclassType(newSt);
            } else {
                // this shouldn't happen - ibator only generates simple names
                throw new ShellException("The Java file merger only supports simple types as super classes");
            }
        } else {
            typeDeclaration.setSuperclassType(null);
        }

        // interface or class?
        if (newJavaFileVisitor.isInterface()) {
            typeDeclaration.setInterface(true);
        } else {
            typeDeclaration.setInterface(false);
        }

        // reconcile the imports
        List<ImportDeclaration> newImports = getNewImports(cu.imports(), newJavaFileVisitor);
        for (ImportDeclaration newImport : newImports) {
            Name name = ast.newName(newImport.getName().getFullyQualifiedName());
            ImportDeclaration newId = ast.newImportDeclaration();
            newId.setName(name);
            cu.imports().add(newId);
        }

        TextEdit textEdit = cu.rewrite(document, null);
        try {
            textEdit.apply(document);
        } catch (BadLocationException e) {
            throw new ShellException(
                    "BadLocationException removing prior fields and methods");
        }

        // regenerate the CompilationUnit to reflect all the deletes and changes
        astParser.setSource(document.get().toCharArray());
        CompilationUnit strippedCu = (CompilationUnit) astParser.createAST(null);

        // find the top level public type declaration
        TypeDeclaration topLevelType = null;
        Iterator iter = strippedCu.types().iterator();
        while (iter.hasNext()) {
            TypeDeclaration td = (TypeDeclaration) iter.next();
            if (td.getParent().equals(strippedCu)
                    && (td.getModifiers() & Modifier.PUBLIC) > 0) {
                topLevelType = td;
                break;
            }
        }

        // now add all the new methods and fields to the existing
        // CompilationUnit with a ListRewrite
        ASTRewrite rewrite = ASTRewrite.create(topLevelType.getRoot().getAST());
        ListRewrite listRewrite = rewrite.getListRewrite(topLevelType,
                TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

        Iterator<ASTNode> astIter = newJavaFileVisitor.getNewNodes().iterator();
        int i = 0;
        while (astIter.hasNext()) {
            listRewrite.insertAt(astIter.next(), i++, null);
        }

        textEdit = rewrite.rewriteAST(document, JavaCore.getOptions());
        try {
            textEdit.apply(document);
        } catch (BadLocationException e) {
            throw new ShellException(
                    "BadLocationException adding new fields and methods");
        }

        String newSource = document.get();
        return newSource;
    }

    private List<Type> getNewSuperInterfaces(List<Type> existingSuperInterfaces, 
            NewJavaFileVisitor newJavaFileVisitor) {

        List<Type> answer = new ArrayList<Type>();

        for (Type newSuperInterface : newJavaFileVisitor.getSuperInterfaceTypes()) {
            if (newSuperInterface.isSimpleType()) {
                SimpleType newSimpleType = (SimpleType) newSuperInterface;
                String newName = newSimpleType.getName().getFullyQualifiedName();
                
                boolean found = false;
                for (Type existingSuperInterface : existingSuperInterfaces) {
                    if (existingSuperInterface.isSimpleType()) {
                        SimpleType existingSimpleType = (SimpleType) existingSuperInterface;

                        String existingName = existingSimpleType.getName().getFullyQualifiedName();

                        if (newName.equals(existingName)) {
                            found = true;
                            break;
                        }
                    }
                }
                
                if (!found) {
                    answer.add(newSuperInterface);
                }
            }
        }

        return answer;
    }

    private List<ImportDeclaration> getNewImports(List<ImportDeclaration> existingImports,
            NewJavaFileVisitor newJavaFileVisitor) {
        List<ImportDeclaration> answer = new ArrayList<ImportDeclaration>();

        for (ImportDeclaration newImport : newJavaFileVisitor.getImports()) {
            String newName = newImport.getName().getFullyQualifiedName();
            boolean found = false;
            for (ImportDeclaration existingImport : existingImports) {
                String existingName = existingImport.getName().getFullyQualifiedName();
                
                if (newName.equals(existingName)) {
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                answer.add(newImport);
            }
        }
        
        return answer;
    }

    /**
     * This method parses the new Java file and returns a
     * filled out NewJavaFileVisitor.  The returned visitor can
     * be used to determine characteristics of the new file, and
     * a lost of new nodes that need to be incorporated into the
     * existing file.
     * 
     * @param astParser
     * @return
     */
    private NewJavaFileVisitor visitNewJavaFile(ASTParser astParser) {
        astParser.setSource(newJavaSource.toCharArray());
        CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
        NewJavaFileVisitor newVisitor = new NewJavaFileVisitor();
        cu.accept(newVisitor);
        
        return newVisitor;
    }
    
    private IFile getFile() throws ShellException {
        IPath path = new Path(existingFilePath);
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IFile file = root.getFileForLocation(path);
        if (file != null && file.exists()) {
            return file;
        } else {
            // this should not happen because ibator only returns the path
            // calculated by the eclipse callback
            StringBuilder sb = new StringBuilder();
            sb.append("The file ");
            sb.append(existingFilePath);
            sb.append(" does not exist in this workspace");
            throw new ShellException(sb.toString());
        }
    }
}