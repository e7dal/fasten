/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.fasten.analyzer.javacgopal.data;

import eu.fasten.analyzer.javacgopal.scalawrapper.JavaToScalaConverter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import eu.fasten.core.data.FastenURI;
import org.opalj.br.ClassHierarchy;
import org.opalj.br.Method;
import org.opalj.br.ObjectType;
import org.opalj.collection.immutable.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//public class Type {
//
//    private static Logger logger = LoggerFactory.getLogger(Type.class);
//
//    private List<Method> methods;
//    private Chain<ObjectType> superClasses;
//    private List<ObjectType> superInterfaces;
//
//    public void setMethods(List<Method> methods) {
//        this.methods = methods;
//    }
//
//    public void setSuperClasses(Chain<ObjectType> superClasses) {
//        this.superClasses = superClasses;
//    }
//
//    public void setSuperInterfaces(List<ObjectType> superInterfaces) {
//        this.superInterfaces = superInterfaces;
//    }
//
//    public List<Method> getMethods() {
//        return methods;
//    }
//
//    public Chain<ObjectType> getSuperClasses() {
//        return superClasses;
//    }
//
//    public List<ObjectType> getSuperInterfaces() {
//        return superInterfaces;
//    }
//
//    public Type() {
//        this.methods = new ArrayList<>();
//        this.superClasses = null;
//        this.superInterfaces = new ArrayList<>();
//    }
//
//    /**
//     * Sets super classes and super interfaces of this type
//     *
//     * @param classHierarchy org.opalj.br.ClassHierarchy
//     * @param currentClass org.opalj.br.ObjectType. The type that its supper types should be set.
//     */
//    public void setSupers(ClassHierarchy classHierarchy, ObjectType currentClass) {
//
//        if (classHierarchy.supertypes().contains(currentClass)) {
//
//            try {
//                this.superClasses = classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s();
//            } catch (NoSuchElementException e) {
//                logger.error("This type {} doesn't have allSuperclassTypesInInitializationOrder method.", currentClass, e);
//            } catch (OutOfMemoryError e) {
//                logger.error("This type {} made an out of memory Exception in calculation of its supper types!", currentClass, e);
//            } catch (Exception e) {
//                logger.error("This type made an Exception in calculation of its supper types!", e);
//            }
//
//            if (superClasses != null) {
//                superClasses.reverse();
//            }
//
//            classHierarchy.allSuperinterfacetypes(currentClass, false).foreach(
//                JavaToScalaConverter.asScalaFunction1(anInterface -> this.superInterfaces.add((ObjectType) anInterface))
//            );
//        }else {
//            logger.warn("Opal class hierarchy didn't include super types of {}", currentClass);
//        }
//    }
//}

public class Type {

    private static Logger logger = LoggerFactory.getLogger(Type.class);

    //Methods that this type implements
    private List<FastenURI> methodsFURI;
    //Classes that this type inherits from in the order of instantiation.
    private LinkedList<FastenURI> superClassesFURI;
    //Interfaces that this type or its super classes implement.
    private List<FastenURI> superInterfacesFURI;

    private List<Method> methods;
    private Chain<ObjectType> superClasses;
    private List<ObjectType> superInterfaces;

    public List<FastenURI> getMethodsFURI() {
        return methodsFURI;
    }

    public LinkedList<FastenURI> getSuperClassesFURI() {
        return superClassesFURI;
    }

    public List<FastenURI> getSuperInterfacesFURI() {
        return superInterfacesFURI;
    }

    public List<Method> getMethods() {
        return methods;
    }

    public Chain<ObjectType> getSuperClasses() {
        return superClasses;
    }

    public List<ObjectType> getSuperInterfaces() {
        return superInterfaces;
    }

    public void setMethodsFURI(List<FastenURI> methods) {
        this.methodsFURI = methods;
    }

    public void setSuperClassesFURI(LinkedList<FastenURI> superClasses) {
        this.superClassesFURI = superClasses;
    }

    public void setSuperInterfacesFURI(List<FastenURI> superInterfaces) {
        this.superInterfacesFURI = superInterfaces;
    }

    public void setMethods(List<Method> methods) {
        this.methods = methods;
    }

    public void setSuperClasses(Chain<ObjectType> superClasses) {
        this.superClasses = superClasses;
    }

    public void setSuperInterfaces(List<ObjectType> superInterfaces) {
        this.superInterfaces = superInterfaces;
    }

    public Type(List<FastenURI> methods, LinkedList<FastenURI> superClasses, List<FastenURI> superInterfaces) {
        this.methodsFURI = methods;
        this.superClassesFURI = superClasses;
        this.superInterfacesFURI = superInterfaces;
    }

    public Type() {
        this.methods = new ArrayList<>();
        this.superClasses = null;
        this.superInterfaces = new ArrayList<>();
    }

        /**
     * Sets super classes and super interfaces of this type
     *
     * @param classHierarchy org.opalj.br.ClassHierarchy
     * @param currentClass org.opalj.br.ObjectType. The type that its supper types should be set.
     */
    public void setSupers(ClassHierarchy classHierarchy, ObjectType currentClass) {

        if (classHierarchy.supertypes().contains(currentClass)) {

            try {
                this.superClasses = classHierarchy.allSuperclassTypesInInitializationOrder(currentClass).s();
            } catch (NoSuchElementException e) {
                logger.error("This type {} doesn't have allSuperclassTypesInInitializationOrder method.", currentClass, e);
            } catch (OutOfMemoryError e) {
                logger.error("This type {} made an out of memory Exception in calculation of its supper types!", currentClass, e);
            } catch (Exception e) {
                logger.error("This type made an Exception in calculation of its supper types!", e);
            }

            if (superClasses != null) {
                superClasses.reverse();
            }

            classHierarchy.allSuperinterfacetypes(currentClass, false).foreach(
                JavaToScalaConverter.asScalaFunction1(anInterface -> this.superInterfaces.add((ObjectType) anInterface))
            );
        }else {
            logger.warn("Opal class hierarchy didn't include super types of {}", currentClass);
        }
    }

}
