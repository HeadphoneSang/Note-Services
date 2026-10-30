package org.template.enums;

/**
 * note 域 Redis Key
 * <p>
 * 认证类 key（CAPTCHA/EMAIL_CODE/LOGIN_FAIL/LOGIN_LOCK/LOGOUT_TOKEN）
 * 已迁至 dto-module 的 AuthRedisKey，由 auth-module 与 gateway 使用。
 */
public enum RedisKey {

    // ---- 业务缓存 ----
    NOTE_BLOCKS  ("note_backbond:note:blocks:%s"),        // 笔记块 Hash，参数 noteId
    NOTE_LIST    ("note_backbond:note:list:user:%s:page:%s:size:%s"), // 笔记列表分页，参数 userId,page,size
    NOTEBOOK_LIST("note_backbond:notebook:list:user:%s"), // 笔记本列表，参数 userId
    USER_INFO    ("note_backbond:user:info:%s"),          // 用户信息，参数 userId
    TAG_LIST     ("note_backbond:tag:user:%s"),           // 标签列表，参数 userId

    // ---- 锁 / 限流 ----
    PAGE_LOCK    ("note_backbond:lock:page:%s:%s"),       // 列表页互斥锁，参数 notebookId,userId
    RATE_LIMIT   ("note_backbond:rate:limit:%s"),         // 接口限流，参数 key
    ;
    private final String pattern;

    RedisKey(String pattern){
        this.pattern = pattern;
    }

    public String build(Object...args){
        return String.format(this.pattern,args);
    }

}
