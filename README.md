

> 最初的时候，在学习路由框架时，发现了 [ActivityRouter](https://github.com/mzule/ActivityRouter)，但是这个项目只有代码实现和使用方法，缺少实现原理的介绍与实现的过程，对于刚刚接触这个 Router 的初学者来说，不知道怎么下口和学习。本篇文章记录如何使用注解处理器，到最终实现了一个最简单版本的 Activity 路由框架。

为了减少学习成本，本 demo 使用 java 语言。代码已上传到 [github](https://github.com/xinwa/RouterProcessDemo)

### 步骤1 - 创建 annotation Module

新建 annotation Java Library 模块，定义 RouterAnnotation 注解，value 则表示页面的 uri 地址。
```
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface RouterAnnotation {
    String value();
}
```

### 步骤二 - 创建 processor Module 
新建 processors Java Library 模块，继承 AbstractProcessor 创建注解处理器.
```
@AutoService(Processor.class)
public class RouterProcessor extends AbstractProcessor {

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        System.out.println("---- process ----")
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
    
```

配置注解处理器，使编译期该注解处理器能参与进来。这里我们使用 google 提供的框架.在 processor 模块下的 build.gradle 添加如下依赖
```
implementation 'com.google.auto.service:auto-service:1.0-rc3'
// 该注解处理器是让 autoService 注解生效
annotationProcessor "com.google.auto.service:auto-service:1.0-rc3"
implementation project(':annotation')
```

### 步骤三 - 配置 app moudle
接下来我们在项目 app 模块下增加我们定义好的注解处理器依赖。

```
implementation project(':annotation')
annotationProcessor project(':processor')
```

给 MainActivity 配置 RouterAnnotation
```
@RouterAnnotation("demo://main_activity")
public class MainActivity extends AppCompatActivity 
```

gradlew build 构建项目，输出 “---- process ----” 则代表注解处理器成功运行。

### 步骤四 - 动态生成路由映射文件
通过上面的例子已成功让注解处理器参与编译过程，接下来我们让 RouterProcess 发挥更大的作用，使其生成 Activity 页面路由表，通过 uri 地址的方式启动 Activity。

首先说下大体的实现思路：**先获取工程中使用 @RouterAnnotation 的地方，通过该注解获取页面 uri 的地址，以及定义该注解 Activity 的 class 名称。然后利用 [javapoet](https://github.com/square/javapoet) 动态生成 Java 文件，该文件将每个页面的 uri 地址，以及对应的 Activity 的 class 名称插入到路由表里。这样通过 router 打开 uri 页面时，就可以进行路由表的匹配，然后通过 startActivity 的方式启动页面**

接下来我们看代码实现流程👇
```
	@Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
    	// 先不管这里，后面我们在看
    	generateRouterInit();
        // 首先获取注解元素
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(RouterAnnotation.class);
        
        // 定义一个 public static 类型的 map 方法
        MethodSpec.Builder mapMethod = MethodSpec.methodBuilder("map").addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);
        
        // 遍历注解元素
        for (Element element: elements) {
            if (element.getKind() == ElementKind.CLASS) {
                RouterAnnotation router = element.getAnnotation(RouterAnnotation.class);
                // 获取 activity 的 class name
                ClassName className = ClassName.get((TypeElement) element);
                // 获取 uri 地址
                String path = router.value();
				// 生成代码 Routers.map(uri, xxx.class); 
                // 这里是将 uri 与 activity记录插入到 Routers 路由表中
                mapMethod.addStatement("com.xiwna.processor.router.Routers.map($S, $T.class, null)", path, className);
            }
        }
        
        mapMethod.addCode("\n");

        // 生成 RouterMapping 文件
        TypeSpec helloWorldClass = TypeSpec.classBuilder("RouterMapping")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(mapMethod.build())
                .build();

        JavaFile javaFile = JavaFile.builder("com.xiwna.processor.router", helloWorldClass)
                .build();

        try {
            javaFile.writeTo(filer);
        } catch (IOException e) {
            // e.printStackTrace();
        }
    }
    
    return false
```

执行 gradlew build 构建，不出意外，会在 app/build/generated/ap_generated_sources 目录下会生成 RouterMapping.java 文件

![](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/41bf809b2d8a4f5f9b4b8b588c6e1abc~tplv-k3u1fbpfcp-zoom-1.image)

如上图所以，我们生成一个 RouterMapping 文件。然后调用 Routers.map 方法插入一条路由记录。方法实现如下，后面我们会专门介绍 Routers 路由表的具体实现。

```
    // 将页面插入到路由表中
    public static void map(String path, Class<? extends Activity> activity, MethodInvoker method) {
        mappings.add(new Mapping(path, activity, method));
    }
```
map 方法将 uri 和 activity class 构造成 Mapping 对象，放入 mappings 的集合中。

接下来我们看怎么让该文件中的 RouterMapping.map 方法执行，使该路由记录插入到路由表中呢。这就用到我们前面说到的 generateRouterInit 方法了。

```
    private void generateRouterInit() {
    	// 生成 public static init 方法
        MethodSpec.Builder initMethod = MethodSpec.methodBuilder("init")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC);
		
        // 该方法实现为 RouterMapping.map()
        initMethod.addStatement("RouterMapping.map()");

		// 生成 RouterInit 方法
        TypeSpec routerInit = TypeSpec.classBuilder("RouterInit")
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(initMethod.build())
                .build();
        try {
            JavaFile.builder("com.xiwna.processor.router", routerInit)
                    .build()
                    .writeTo(filer);
        } catch (Exception e) {
           // e.printStackTrace();
        }
    }
```

![](https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/4391a96766404b679dd267ac1f7ced16~tplv-k3u1fbpfcp-zoom-1.image)

看到这里明白了，我们新生成了文件，来进行路由表的初始化。至此，注解处理器的工作就完成了。


### 步骤五 - 使用 Router 打开页面 uri
接下来我们 Routers 路由表的实现。

```
// 路由记录
public class Mapping {
    private final String path;
    private final Class<? extends Activity> activity;
    private final MethodInvoker method;
    private String formatHost;

    public Mapping(String path, Class<? extends  Activity> activity, MethodInvoker method) {
        this.path = path;
        this.activity = activity;
        this.method = method;
        formatHost = Uri.parse(path).getHost();
    }
}

public class Routers {
	// 页面路由表
    private static List<Mapping> mappings = new ArrayList<>();

    // 路由表的初始化
    private static void initIfNeed() {
        if (mappings.isEmpty()) {
            RouterInit.init();
        }
    }
    
    // 将页面插入到路由表中
    public static void map(String path, Class<? extends Activity> activity, MethodInvoker method) {
        mappings.add(new Mapping(path, activity, method));
    }

    /**
     * 通过 router 打开 activity
     *
     * @param context
     * @param url
     * @return
     */
    public static boolean open(Context context, String url) {
        initIfNeed();
        Uri uri = Uri.parse(url);
        // 遍历路由表，进行 uri 的匹配，匹配成功，则启动对面的 Activity 页面
        for (Mapping mapping : mappings) {
            if (mapping.match(uri)) {
                Intent intent = new Intent(context, mapping.getActivity());
                intent.putExtras(mapping.parseExtras(uri));
                context.startActivity(intent);
                return true;
            }
        }
        return false;
    }
}
```

通过上面的代码我们得知，该路由表的实现内容是提供 map 方法，存储前面注解处理器生成的 RouterMapping 路由映射记录，保存到 mappings 列表中。然后打开 uri 时，进行路由表的初始化，并匹配合适的页面。


匹配规则比较简单，主要是判断 host 地址是否相同
```
public boolean match(Uri uri) {
	return this.formatHost.equals(uri.getHost());
}
```
匹配通过后，进行 Activty 的启动，如果 uri 链接中，有使用参数，则进行解析。
```
public Bundle parseExtras(Uri uri) {
	Bundle bundle = new Bundle();
	Set<String> names = UriCompact.getQueryParameterName(uri);
	for (String name: names) {
	String value = uri.getQueryParameter(name);
	put(bundle, name, value);
	}
	return bundle;
}
```

启动目标 Activity 页面
```
{
	intent intent = new Intent(context, mapping.getActivity());
	intent.putExtras(mapping.parseExtras(uri));
	context.startActivity(intent);
}

```

### 步骤六 - 增加 stub 模块
此时，进行 gradlew build 构建，会发生编译错误。那是因为 Routers 类中 initIfNeed 方法中的
RouterInit.init() 方法是在编译过程中才生成的。所以我们需要在编译期间就有 RouterInit 和 RouterMapping 文件。

这里我们增加 stub 模块，并只在编译期间依赖，使用 compileOnly project(':stub') 方式。
通过这种方式，就可以让编译通过，并且在打包运行时，使用的是注解处理器生成的文件。

![](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/1550fc59e6b94431bb860f3e6b3bcc2d~tplv-k3u1fbpfcp-zoom-1.image)
![](https://p6-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/b9c2ea35b8304ca4a3865a3c2b9bd4f3~tplv-k3u1fbpfcp-zoom-1.image)


至此一个完整的简单版本的 Router 框架就实现了。在组建化项目中，每个单独的模块都只需要插入路由记录到路由表中，这样作为基础模块的 Router，就可以打开各个模块的页面了。希望小伙伴们通过该 demo，也能一步一步实现 ActivityRouter。
