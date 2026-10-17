function ShowDialog(txt) {
    console.log(txt);
    $(".toast-win").text(txt);
    $(".toast-win").show();
    setTimeout(function () { $(".toast-win").hide() }, 2000);
}
function ShowSuccess(txt) {
    console.log(txt);
    $(".toast-win").text(txt);
    $(".toast-win").show();
    setTimeout(function () { $(".toast-win").hide() }, 2000);
}
function isLogin() {
    var ustatus = getLoginStatus();
    if (!ustatus || ustatus == "0") {
        return false;
    }
    return true;
}
function getLoginStatus() {
    var result;
    $.ajax({
        url: '/showstatus.ashx?d=' + new Date().getTime(),
        async: false,
        error: function (msg) {
            result = "0";
        },
        success: function (json) {
            result = json;
        }
    });
    return result;
}
function isAllowCookie() {
    //if (!(document.cookie || navigator.cookieEnabled)) {
    //    return false;
    //}
    //return true;
    return false;
}
function isRealNum(val) {
    if (val === "" || val == null) {
        return false;
    }
    if (!isNaN(val)) {
        return true;
    } else {
        return false;
    }
}

var layoutbackarr = new Array();
var laytype = 0;
var layoutobj = new Object();
var maxlayoutcount = 10;

function layoutlBack(defaulturl,isreload) {

    if (!isAllowCookie()) {
        if (typeof defaulturl == "undefined") {
            defaulturl = "/";
        }
        if (history.length != 1&&document.referrer && document.referrer.indexOf(window.location.host) >= 0) {
            if (isreload) {
                history.back();
            }
            else {
                history.back();
            }
        }
        else {
            window.location.href = defaulturl;
        }
    }
    else {
        if ($.cookie("layoutlback") == "" || $.cookie("layoutlback") == null) {
            window.location.href = defaulturl;
            return;
        }
        try {
            layoutbackarr = JSON.parse($.cookie("layoutlback"));
        }
        catch (e) {
            layoutbackarr = new Array();
            $.cookie('layoutlback', '', { expires: -1, path: '/' });
        }

        while (layoutbackarr.length > 0) {
            var layout = layoutbackarr.pop();
            if (layout.Layout < laytype) {
                window.location.href = layout.Url;
                return;
            }
        }
        var objString = JSON.stringify(layoutbackarr);
        $.cookie('layoutlback', objString, { path: '/' });
        window.location.href = defaulturl;

    }
}

function addLayoutBack() {
    if (laytype > 1) {
        if ($.cookie("layoutlback") == "" || $.cookie("layoutlback") == null) {
            layoutbackarr = new Array();
        }
        else {
            try {
                layoutbackarr = JSON.parse($.cookie("layoutlback"));
            }
            catch (e) {
                layoutbackarr = new Array();
                $.cookie('layoutlback', '', { expires: -1, path: '/' });
            }
        }
        if (layoutbackarr.length >= maxlayoutcount) {
            layoutbackarr.shift();
        }
        var newlayoutarr = new Array();
        for (var i = 0; i < layoutbackarr.length; i++) {
            if (layoutbackarr[i].Layout < laytype) {
                newlayoutarr.push(layoutbackarr[i]);
            }
        }
        layoutbackarr = newlayoutarr;
        //if (layoutbackarr.length > 0 && layoutbackarr[layoutbackarr.length - 1].Layout == laytype)
        //{
        //    layoutbackarr.pop();
        //}
        layoutobj.Layout = laytype;
        layoutobj.Url = window.location.href;
        layoutbackarr.push(layoutobj);
        var objString = JSON.stringify(layoutbackarr);
        $.cookie('layoutlback', objString, { path: '/' });
    }
    else if (laytype == 1) {
        layoutbackarr = new Array();
        $.cookie('layoutlback', '', { expires: -1, path: '/' });
    }
}


function urlReplace(url)
{
    if (window.history.replaceState) {
        window.history.replaceState(null, '', url);
        $(window).scrollTop(0);
        window.location.reload();
    }
    else {
        window.location.replace(url);
    }
}
