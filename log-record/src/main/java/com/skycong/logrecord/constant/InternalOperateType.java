package com.skycong.logrecord.constant;

/**
 * @author ruanmingcong (005163) on 2022/4/21 18:08
 */
public interface InternalOperateType {

    ///////////////////////////////////////////////////////////////////////////
    // db common use
    ///////////////////////////////////////////////////////////////////////////
    String Create = "create";
    String Insert = "insert";
    String Delete = "delete";
    String Update = "update";
    String Select = "select";
    String Export = "export";
    String Import = "import";
    String Sync = "sync";

    ///////////////////////////////////////////////////////////////////////////
    // file common use
    ///////////////////////////////////////////////////////////////////////////
    String Open = "open";
    String Save = "save";
    String Move = "move";
    String Read = "read";
    String Write = "write";
    String Append = "append";
    String Close = "close";

    ///////////////////////////////////////////////////////////////////////////
    // data common use
    ///////////////////////////////////////////////////////////////////////////
    String Add = "add";
    String Query = "query";
    String Sort = "sort";
    String Diff = "diff";
    String Upgrade = "upgrade";
    String Other = "other";

}
