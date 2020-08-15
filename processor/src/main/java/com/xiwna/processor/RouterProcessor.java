package com.xiwna.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import com.xiwna.annotation.RouterAnnotation;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

/**
 * @author xingping
 */
@AutoService(Processor.class)
public class RouterProcessor extends AbstractProcessor {
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        generateRouterInit();
        return handleRouter(roundEnvironment);
    }

    private void generateRouterInit() {
        MethodSpec.Builder initMethod = MethodSpec.methodBuilder("init")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);

        initMethod.addStatement("RouterMapping.map()");

        TypeSpec routerInit = TypeSpec.classBuilder("RouterInit")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(initMethod.build())
                .build();
        try {
            JavaFile.builder("com.xiwna.processor.router", routerInit)
                    .build()
                    .writeTo(filer);
        } catch (Exception e) {
//            e.printStackTrace();
        }
    }

    private boolean handleRouter(RoundEnvironment roundEnv) {
        System.out.println("---------- handle router start ----------------");
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(RouterAnnotation.class);

        MethodSpec.Builder mapMethod = MethodSpec.methodBuilder("map")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);

        for (Element element: elements) {
            if (element.getKind() == ElementKind.CLASS) {
                RouterAnnotation router = element.getAnnotation(RouterAnnotation.class);
                ClassName className = ClassName.get((TypeElement) element);
                String path = router.value();

                mapMethod.addStatement("com.xiwna.processor.router.Routers.map($S, $T.class, null)", path, className);
            }
        }
        mapMethod.addCode("\n");

        TypeSpec helloWorldClass = TypeSpec.classBuilder("RouterMapping")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(mapMethod.build())
                .build();

        JavaFile javaFile = JavaFile.builder("com.xiwna.processor.router", helloWorldClass)
                .build();

        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
//            e.printStackTrace();
        }
        System.out.println("---------- handle router end ----------------");
        return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> annotations = new LinkedHashSet<>();
        annotations.add(RouterAnnotation.class.getCanonicalName());
        return annotations;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }
}
