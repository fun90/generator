/**
 *    Copyright 2006-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.generator.internal;

import org.mybatis.generator.api.CommentGenerator;
import org.mybatis.generator.api.IntrospectedColumn;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.api.dom.xml.XmlElement;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

/**
 * @author xionglingcong 2017/9/21
 * @since 1.3.6
 */
public class SimpleCommentGenerator implements CommentGenerator
{
    protected SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd");
    protected String author;
    protected String version;

    @Override
    public void addConfigurationProperties(Properties properties)
    {
        author = properties.getProperty("author");
        version = properties.getProperty("version");
    }

    @Override
    public void addFieldComment(Field field, IntrospectedTable introspectedTable, IntrospectedColumn introspectedColumn)
    {
        field.addJavaDocLine("/**"); //$NON-NLS-1$
        field.addJavaDocLine((" * " + introspectedColumn.getRemarks())); //$NON-NLS-1$
        field.addJavaDocLine(" */"); //$NON-NLS-1$
    }

    @Override
    public void addFieldComment(Field field, IntrospectedTable introspectedTable)
    {

    }

    @Override
    public void addModelClassComment(TopLevelClass topLevelClass, IntrospectedTable introspectedTable)
    {
        topLevelClass.addJavaDocLine("/**");
        topLevelClass.addJavaDocLine(" * " + introspectedTable.getRemarks());
        if(author != null && !author.isEmpty())
            topLevelClass.addJavaDocLine(" * @author " + author + " " + dateFormat.format(new Date()));
        if(version != null && !version.isEmpty())
            topLevelClass.addJavaDocLine(" * @since " + version);
        topLevelClass.addJavaDocLine(" */");
    }


    @Override
    public void addClassComment(InnerClass innerClass, IntrospectedTable introspectedTable)
    {
    }

    @Override
    public void addClassComment(InnerClass innerClass, IntrospectedTable introspectedTable, boolean markAsDoNotDelete)
    {

    }

    @Override
    public void addEnumComment(InnerEnum innerEnum, IntrospectedTable introspectedTable)
    {

    }

    @Override
    public void addGetterComment(Method method, IntrospectedTable introspectedTable,
                                 IntrospectedColumn introspectedColumn)
    {

    }

    @Override
    public void addSetterComment(Method method, IntrospectedTable introspectedTable,
                                 IntrospectedColumn introspectedColumn)
    {

    }

    @Override
    public void addGeneralMethodComment(Method method, IntrospectedTable introspectedTable)
    {

    }

    @Override
    public void addJavaFileComment(CompilationUnit compilationUnit)
    {

    }

    @Override
    public void addComment(XmlElement xmlElement)
    {

    }

    @Override
    public void addRootComment(XmlElement rootElement)
    {

    }
}
