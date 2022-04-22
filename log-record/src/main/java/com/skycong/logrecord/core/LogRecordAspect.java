package com.skycong.logrecord.core;

import com.skycong.logrecord.pojo.LogRecordPojo;
import com.skycong.logrecord.service.LogRecordService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.AnnotatedElementKey;
import org.springframework.context.expression.CachedExpressionEvaluator;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 切面处理逻辑
 *
 * @author ruanmingcong (005163) on 2022/4/22 11:20
 */
@Aspect
public class LogRecordAspect {

    public static final Logger LOGGER = LoggerFactory.getLogger(LogRecordAspect.class);

    /**
     * 模板解析器
     */
    private final LogRecordExpressionEvaluator logRecordExpressionEvaluator;

    private final LogRecordService logRecordService;

    public LogRecordAspect(LogRecordService logRecordService) {
        this.logRecordExpressionEvaluator = new LogRecordExpressionEvaluator();
        this.logRecordService = logRecordService;
        LOGGER.debug("LogRecordAspect init success LogRecordService = {}", this.logRecordService.getClass());
    }


    /**
     * 缓存日志注解, 避免重复解析 SpEL 模板
     */
    private final ConcurrentHashMap<AnnotatedElementKey, LogRecord> targetAnnotationCache = new ConcurrentHashMap<>();

    /**
     * 切点
     */
    @Around("@annotation(LogRecord)")
    public Object process(ProceedingJoinPoint pjp) throws Throwable {
        return deal(pjp);
    }

    /**
     * 切点处理逻辑
     */
    private Object deal(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        // 目标方法
        Method targetMethod = methodSignature.getMethod();
        // 方法入参
        Object[] args = pjp.getArgs();
        // 目标方法所属class
        Class<?> targetClass = pjp.getTarget().getClass();
        // 优先从缓存中获取方法上的注解信息
        AnnotatedElementKey cacheKey = new AnnotatedElementKey(targetMethod, targetClass);
        LogRecord logRecord = targetAnnotationCache.get(cacheKey);
        if (logRecord == null) {
            LogRecord v = targetMethod.getAnnotation(LogRecord.class);
            targetAnnotationCache.put(cacheKey, v);
            logRecord = v;
        }
        // SpEL解析上下文
        LogRecordEvaluationContext ctx = new LogRecordEvaluationContext(targetMethod, args);

        // 初始化context
        LogRecordContext.init();

        Object res = null;
        Throwable throwable = null;
        String errMsg = "";
        // 切点执行 开始============================================================================
        try {
            res = pjp.proceed();
        } catch (Throwable t) {
            throwable = t;
            errMsg = "执行切点方法异常 :" + t.getMessage();
        }
        // 切点执行 结束============================================================================

        // 执行记录日志操作 开始============================================================================
        try {
            ctx.setContextVariables();
            ctx.setRetAndErrMsg(res, errMsg);
            ctx.setFunctions(logRecordService.getFunctions());
            recordLog(new LogRecordPojo(logRecord.value(), logRecord.operator(), logRecord.operateType(), logRecord.businessType(), logRecord.businessDataId(), logRecord.businessDataDetail()), cacheKey, ctx);
        } catch (Exception t) {
            LOGGER.error("持久化日志方法执行出错", t);
        } finally {
            // 清空context
            LogRecordContext.clear();
        }
        // 执行记录日志操作 结束============================================================================

        // 执行切点方法时有异常直接抛出，否则正常返回
        if (throwable != null) {
            LOGGER.error(errMsg, throwable);
            throw throwable;
        }
        return res;
    }

    /**
     * 解析 注解上的spel 表达式
     */
    private void recordLog(LogRecordPojo logRecordPojo, AnnotatedElementKey methodKey, LogRecordEvaluationContext ctx) {
        String valueEl = logRecordPojo.getValue();
        String operatorEl = logRecordPojo.getOperator();
        String businessDataIdEl = logRecordPojo.getBusinessDataId();
        String businessDataDetailEl = logRecordPojo.getBusinessDataDetail();
        // 解析表达式
        logRecordPojo.setValue(logRecordExpressionEvaluator.parseExpression(valueEl, methodKey, ctx));
        logRecordPojo.setOperator(logRecordExpressionEvaluator.parseExpression(operatorEl, methodKey, ctx));
        logRecordPojo.setBusinessDataId(logRecordExpressionEvaluator.parseExpression(businessDataIdEl, methodKey, ctx));
        logRecordPojo.setBusinessDataDetail(logRecordExpressionEvaluator.parseExpression(businessDataDetailEl, methodKey, ctx));
        // 获取操作人
        try {
            logRecordPojo.setOperator(logRecordService.getCurrentOperator(logRecordPojo.getOperator()));
        } catch (Throwable e) {
            LOGGER.error("调用 operatorService.getCurrentOperator 异常", e);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("SpEl解析后的值为: {}, 原始注解值: valueEl = {},operatorEl = {},businessDataIdEl = {},businessDataDetailEl = {}",
                    logRecordPojo, valueEl, operatorEl, businessDataIdEl, businessDataDetailEl);
        }
        // 记录日志
        try {
            logRecordService.record(logRecordPojo);
        } catch (Throwable e) {
            LOGGER.error("调用 RecordLogService.record 异常", e);
        }
    }


    /**
     * spel 解析器， 对 {@link SpelExpressionParser} 的包装
     */
    public static class LogRecordExpressionEvaluator extends CachedExpressionEvaluator {
        private final Logger log = LoggerFactory.getLogger(LogRecordExpressionEvaluator.class);

        private final static Map<ExpressionKey, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>(64);

        public LogRecordExpressionEvaluator() {
            super(new SpelExpressionParser());
        }

        /**
         * 解析注解上的spel表达式，返回结果string
         */
        public String parseExpression(String conditionExpression, AnnotatedElementKey methodKey, EvaluationContext evalContext) {
            if (conditionExpression == null || conditionExpression.isEmpty()) return "";
            String value = "";
            try {
                value = getExpression(EXPRESSION_CACHE, methodKey, conditionExpression).getValue(evalContext, String.class);
            } catch (Exception e) {
                log.error("SpEl解析错误! 错误原因: {} {}", e.getMessage(), e);
            }
            return value;
        }

    }


    /**
     * SpEL 解析时的上下文，用来保存方法执行时的上下文参数，spel执行时从这里获取变量
     * {@link LogRecordAspect.LogRecordExpressionEvaluator#parseExpression}
     */
    public static class LogRecordEvaluationContext extends MethodBasedEvaluationContext {

        private static final ParameterNameDiscoverer PARAM_DISCOVER = new DefaultParameterNameDiscoverer();

        public LogRecordEvaluationContext(Method targetMethod, Object[] args) {
            //把方法的参数都放到 SpEL 解析的 RootObject 中
            super(TypedValue.NULL, targetMethod, args, PARAM_DISCOVER);
        }

        /**
         * 把 LogRecordContext 中的变量都放到 RootObject 中 <p>
         * LogRecord标注的方法执行后，上下文中才可能存在值
         */
        public void setContextVariables() {
            Map<String, Object> variables = LogRecordContext.getVariables();
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                setVariable(entry.getKey(), entry.getValue());
            }
        }

        /**
         * 把方法的返回值和 errMsg 都放到 RootObject 中
         * LogRecord标注的方法执行后，上下文中才可能存在返回值和错误信息
         *
         * @param ret    返回值
         * @param errMsg 错误信息
         */
        public void setRetAndErrMsg(Object ret, String errMsg) {
            if (ret != null) {
                setVariable("_ret", ret);
            }
            if (errMsg != null) {
                setVariable("_errMsg", errMsg);
            }
        }

        /**
         * 设置自定义函数
         */
        public void setFunctions(Map<String, Method> map) {
            for (Map.Entry<String, Method> entry : map.entrySet()) {
                setVariable(entry.getKey(), entry.getValue());
            }
        }

    }

}
