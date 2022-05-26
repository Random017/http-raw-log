#!/bin/bash

#
function run {
    #第一个参数 指定运行环境
    profile="$1"
    isDebug="$2"
    if [[ $isDebug != "y" ]]; then
	      isDebug="n"
    fi
    pwd
    echo "1：是否Debug模式运行？（y/n） ${isDebug}"
    echo "2：运行环境profile=${profile}"
#    获取 app jar 文件
    jarfile=$(ls -lt *SNAPSHOT.jar | head -n 1 |awk '{print $9}')
    if [[ "$?" == "0" ]]; then
        stop $profile $jarfile
    fi
    if [[ $isDebug == "y" ]]; then
	echo "3：Debug模式启动..."
	echo "4：启动命令====> nohup java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=19999 -Xmx256m -Xms64m -server -Djava.security.egd=file:/dev/./urandom -jar ${jarfile} --spring.profiles.active=${profile} >/dev/null 2>&1 &"
	nohup java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=19999 -Xmx256m -Xms64m -server -Djava.security.egd=file:/dev/./urandom-jar ${jarfile} --spring.profiles.active=${profile} >/dev/null 2>&1 &
    else
	echo "3：正常模式启动..."
	echo "4：启动命令====> nohup java -Xmx512m -Xms64m -server -Djava.security.egd=file:/dev/./urandom -jar ${jarfile} --spring.profiles.active=${profile} >/dev/null 2>&1 &"
	nohup java -Xmx512m -Xms64m -server -Djava.security.egd=file:/dev/./urandom -jar ${jarfile} --spring.profiles.active=${profile} >/dev/null 2>&1 &
    fi
    echo "5:启动应用中，请查看日志文件.../data/log "
}

function stop {
    profile="$1"
    jarfile="$2"
#    只截取 以 -SNAPSHOT-boot.jar 结尾的jar
    temp=${jarfile%-SNAPSHOT.jar}
    jarfile2=${temp%-*}
    echo "停止的工程 jar 名：$jarfile2"
    ps aux | grep "${jarfile2}" | grep "spring.profiles.active=${profile}" | grep -v grep > /dev/null
    if [[ "$?" == "0" ]]; then
        echo "该应用还在跑，我先停了它"
        pid=$(ps aux | grep "${jarfile2}" | grep "spring.profiles.active=${profile}" | grep -v grep |awk '{print $2}')
        if [[ "$pid" != "" ]]; then
            kill -9 $pid
        fi
        echo "停止应用成功..."
    fi
}

if [[ "$1" == "run" ]]; then

    if [[ "$#" < 2 ]]; then
        echo "请输入正确参数：./run.sh run {profile}"
        exit 1
    fi
    profile="$2"
    isDebug="$3"
    if [[ "$profile" != "local" && "$profile" != "dev" && "$profile" != "test" && "$profile" != "prod" && "$profile" != "production" ]]; then
        echo "参数错误，请输入正确的profile参数，使用方法："
        echo "./run.sh run {profile}    ==> 启动应用，{profile}取值：local|dev|test|prod|production"
        exit 1
    fi
    run "${profile}" "${isDebug}"
elif [[ "$1" == "stop" ]]; then
    if [[ "$#" < 2 ]]; then
        echo "请输入正确参数：./run.sh stop  {profile}"
        exit 1
    fi
    profile="$2"
    if [[ "$profile" != "local" && "$profile" != "dev" && "$profile" != "test" && "$profile" != "prod" && "$profile" != "production" ]]; then
        echo "参数错误，请输入正确的profile参数，使用方法："
        echo "./run.sh stop {profile}     ==> 停止应用，{profile}取值：local|dev|test|prod|production"
        exit 1
    fi
    jarfile=$(ls -lt *.jar | head -n 1 |awk '{print $9}')
    stop $profile $jarfile
else
    echo "参数错误，使用方法：{}参数是必填的，[]参数可选"
    echo "./run.sh run {profile}    ==> 启动应用，{profile}取值：local|dev|test|prod|production"
    echo "./run.sh stop  {profile}    ==> 停止应用，{profile}取值：local|dev|test|prod|production"
    exit 1
fi