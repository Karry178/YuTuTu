这是一个多人协同编辑图片的 WebSocket 系统，使用了 Disruptor 高性能消息队列来处理并发消息，确保多用户同时编辑图片时的性能和数据一致性。

# 开发流程：

## 第一步：定义消息模型 model 文件夹

### 1.PictureEditMessageTypeEnum 消息类型枚举类：

定义 WebSocket 消息的 5 中类型

### 2.PictureEditActionEnum 编辑操作枚举类：

定义具体的编辑动作

### 3.PictureEditRequestMessage 请求消息实体：

前端发给后端的请求，主要定义了消息类型和执行的编辑动作：

```java
{
    "type":"EDIT_ACTION"  // 消息类型
    "editAction":"ZOOM_IN"  // 具体操作
}
```

### 4.PictureEditResponseMessage 响应消息实体：

后端广播给所有客户端的消息格式，定义了消息类型、执行的编辑动作、信息和用户信息(UserVO)：

```java
{
    "type":"EDIT_ACTION"  // 消息类型
    "message":"用户 %s 执行了 %s 动作"  // 用户执行的动作信息
    "editAction":"ZOOM_IN"  // 具体操作
    "user":{}
}
```

## 第二步：配置 WebSocket

### 1.WebSocketConfig：WebSocket 配置类

作用：启用 WebSocket 并注册端点，要加入注解：@Configuration 配置类、@EnableWebSocket 开启 WebSocket；

```java
// 注册WebSocket端点：/ws/picture/edit
registry.addHandler(PictureEditHandler, "/ws/picture/edit")
        // 添加握手拦截器
    	.addInterceptors(wsHandshakeInterceptor)
    	// 允许跨域
    	.setAllowOrigins("*");
```

前端连接方式：ws://localhost:8123/api/ws/picture/edit?pictureId=123

## 第三步：握手拦截器（权限校验）

### 1.WsHandshakeInterceptor 握手拦截器

作用：在建立 WebSocket 连接前进行权限校验

检验流程：

```markdown
1. 检查 pictureId 参数是否存在
2. 检查用户是否登录
3. 检查图片是否存在
4. 检查图片所在空间是否为团队空间
5. 检查用户是否有编辑权限（PICTURE_EDIT）
6. 校验通过后，将 user、userId、pictureId 存入 WebSocket Session
```

## 第四步：Disruptor 高性能消息队列

### 1.PictureEditEvent - 事件实体

作用：定义放入 Disruptor 环形队列的事件对象

```java
{
  pictureEditRequestMessage,  // 请求消息
  session,                    // WebSocket 会话
  user,                       // 当前用户
  pictureId                   // 图片 ID
}
```

### 2.PictureEditEventDisruptorConfig - Disruptor 配置

作用：创建并启动 Disruptor 环形队形

为什么用 Disruptor？

1. 高性能：比 BlockingQueue 快 10 倍以上
2. 无锁设计：避免线程竞争
3. 适合高并发场景：多用户同时编辑图片

```java
int bufferSize = 1024 * 256;  // 环形队列大小：256K

// 创建 Disruptor
Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
    PictureEditEvent::new,     // 事件工厂
    bufferSize,                // 缓冲区大小
    // 自定义线程工厂
    ThreadFactoryBuilder.create()
                        .setNamePrefix("pictureEditEventDisruptor")
                        .build()
);

// 绑定消费者（WorkHandler）
disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);

// 启动
disruptor.start();
```

### 3.PictureEditEventProducer - 消息生产者

作用：将前端发来的消息放入 Disruptor 队列，发布事件

流程：

```markdown
1. 获取 RingBuffer
2. 获取下一个可用位置（next）
3. 将消息封装到事件对象中
4. 发布事件（publish）
```

优雅停机：

```java
@PerDestroy  // 应用关闭时执行
public void destroy() {
    // 调用Disruptor对象
    pictureEditEventDisruptor.shutdown();
}
```

### 4.PictureEditEventWorkHandler - 消息消费者

作用：从 Disruptor 队列中取出消息并处理

处理逻辑：

```java
switch(消息类型){
        case ENTER_EDIT:   // 进入编辑
        pictureEditHandler.

handleEnterEditMessage(...);
        break;
                case EXIT_EDIT:    // 退出编辑
        pictureEditHandler.

handleExitEditMessage(...);
        break;
                case EDIT_ACTION:  // 执行编辑操作
        pictureEditHandler.

handleEditActionMessage(...);
        break;
default:           // 错误消息
返回错误提示;
}
```

## ※第五步：WebSocket 消息处理器（核心业务逻辑）

### 1.PictureEditHandler - 消息处理器

作用：处理 WebSocket 连接、消息收发、状态管理

核心数据结构：

```java
// 每张图片的编辑状态（key: pictureId, value: 正在编辑的用户ID）
Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

// 所有连接的会话（key: pictureId, value: 该图片的所有 WebSocket 会话）
// 必须用并发的HashMap，保证线程安全，选择ConcurrentHashMap
Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();
```

pictureSession 是所有连接的会话集合，即该图片所有的 WebSocket 会话！

关键方法：

#### 1.afterConnectEstablished() - 连接建立

```markdown
1.将拿到的session保存到pictureSession集合中
2.构造"用户 xx 加入编辑"的通知消息
3.构造响应：广播给该图片的所有用户
```

#### 2.handleTextMessage() - 收到消息

```markdown
1.解析前端传来的JSON消息并转为PictureEditMessage
2.调用Disruptor环形队列的方法：图片编辑事件的生产者 PictureEditEventProducer（异步处理）
```

#### 3.handleEnterEditMessage() - 进入编辑

```markdown
1.检查是否有其他用户正在编辑
2.如果没有，设置当前用户为编辑者
3.构造响应：广播"用户 xx 开始编辑图片"
```

限制：一张图同时只允许一人编辑！

#### 4.handleEditActionMessage() - 执行编辑操作

```markdown
1.检查当前用户是否为编辑者
2.如果是，广播编辑操作给其他用户
3.构造响应：广播操作，但是要排除当前用户（避免重复编辑）
```

#### 5.handleExitEditMessage() - 退出编辑

```markdown
1.检查当前用户是否为编辑者
2.如果是，移除其编辑状态
3.构造响应：广播"用户 XX 退出了编辑"
```

#### 6.afterConnectionClosed() - 连接关闭

```markdown
1.自动退出编辑状态
2.删除会话：从pictureSessions中移除会话
3.构造响应：广播"用户 XX 离开图片编辑"
```

#### 通用方法：broadcastToPicture() - 广播消息

```markdown
1.从pictureSessions中获取该图片的所有WebSocket会话
2.为了避免前端精度丢失，使用Jackson将Long类型转为String
3.遍历所有会话，发送消息
4.同时，新建一个broadcastToPicture()方法，支持排除某个会话（避免重复发送）
```

# 完整流程：

## 场景：用户 A 和用户 B 同时编辑图片 1

### 1.用户 A 连接

```java
前端 ->ws://localhost:8123/api/ws/picture/edit?pictureId=1
        ↓
WsHandshakeInterceptor  // WebSocket的拦截器：建立连接前的校验权限
	 ↓
             PictureEditHandler.

afterConnectionEstablished()  // 图片编辑的WebSocket处理器 - 建立连接
	 ↓
广播："用户 A 加入编辑"
```

### 2.用户 B 连接（步骤同用户 A）

```java
前端 ->ws://localhost:8123/api/ws/picture/edit?pictureId=1
        ↓
WsHandshakeInterceptor  // WebSocket的拦截器：建立连接前的校验权限
	 ↓
             PictureEditHandler.

afterConnectionEstablished()  // 图片编辑的WebSocket处理器 - 建立连接
	 ↓
广播："用户 B 加入编辑"
```

### 3.用户 A 进入编辑状态

```java
前端发送：{"type":"ENTER_EDIT"}
        ↓
        PictureEditHandler.

handleTextMessage()  // 图片编辑的WebSocket处理器 - 收到前端信息，根据消息类型处理消息
	↓
            PictureEditEventProducer.

publishEvent() ->Disruptor  // 图片编辑事件生产者 - 通过把前端信息放入Disruptor环形队列 - 发布事件
	↓
            PictureEditEventWorkHandler.

onEvent()  // 图片编辑事件处理器（消费者），针对不同的消息类型处理信息
    ↓
            PictureEditHandler.

handleEnterEditMessage()  // 图片编辑的WS处理器 - 用户A进入编辑状态
    ↓
设置pictureEditingUsers[1]=A的User.

Id()
    ↓
广播"用户 A 开始编辑图片"
```

### 4.用户 A 执行放大操作

```java
前端发送：{"type":"EDIT_ACTION","editAction":"ZOOM_IN"}
        ↓
Disruptor // Disruptor环形队列处理
    ↓
            PictureEditHandler.

handleEditActionMessage()  // 图片编辑的WS处理器 - 用户A执行图片操作
    ↓
广播给用户B："用户 A 执行了 放大 操作"  // 要排除用户A，避免重复
```

### 5.用户 A 退出编辑

```java
前端发送：{"type":"EXIT_EDIT"}
    ↓
移除 pictureEditingUsers[1]  // 给pictureEditingUsers移除用户A在图片1的编辑
    ↓
广播："用户 A 退出了编辑"
```

### 6.用户 A 断开连接

```java
pictureEditHandler.afterConnectionClosed()
    ↓
自动退出编辑状态
    ↓
然后把pictureId从sessionMap中移除，然后把pictureSession移除
    ↓
广播："用户 A 离开了图片编辑"
```



