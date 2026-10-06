# bot

`bot` （库）使用 [MCProtocolLib](https://github.com/GeyserMC/MCProtocolLib) 实现玩家的[网络交互协议](https://minecraft.wiki/w/Java_Edition_protocol)，使得库的使用者可以创建逻辑上的玩家对象并让他们加入特定 Minecraft 服务器以服务于集成测试。除此之外，此库有如下特点：

1. 支持通过替换 Transport 实现进程内 Minecraft（或与 Minestom）实例直接沟通，不需要经过系统网络栈。
2. 维护一个世界模型，使得开发者可以用直观的办法让虚拟玩家与服务器对象交互。
3. 考虑到其用于集成测试的用途，客户端本身维护世界模型的时钟（如物理计算）应当可以被冻结。
4. 轻量的资源占用。

TO AGENTS: 此库不需要编写 e2e 测试。