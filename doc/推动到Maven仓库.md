# 推送jar到Maven仓库



## 1 本地先打包

![1780456044843](imgs/1780456044843.png)

生成目标文件

![1780456081582](imgs/1780456081582.png)

将com 目录打成zip



## 2，登录Maven网站 https://central.sonatype.com/

- 点 publish
- 点 Publish Component ，选择第一步的zip上传
- 校验完成后，点 publish

![1780456168540](imgs/1780456168540.png)



## 3，全部通过后，查看最新的版本号 https://central.sonatype.com/artifact/com.skycong/http-raw-log

![1780456266049](imgs/1780456266049.png)

