function notice(msg) {
    alert(msg);
}
function noticeLogin() {
    notice("You need to login first!");
    location.href = '/login';
}
function uriEncode(url) {
    return encodeURIComponent(url);
}
//mm means more manga
// more list loading
var $mll = 0;
// more list page
var $mlp = 2;
// more list append selector
var $mls = '';
// more list type
var $mlt = '';
// more list more params
var $mlm = '';
// more list finished
var $mlf = false;
// more list triger selector
var $mlts = '';
// more list action
var $mla = null;
var more_list = function() {
    console.log($mlt);
    var u = '';
    switch ($mlt) {
        case 'more': u = 'more';break;
        case 'hot': u = 'hot';break;
        case 'directory': u = 'directory';break;
        case 'search': u = 'search';break;
        case 'spoiler': u = 'spoiler';break;
        case 'news': u = 'news';break;
        case 'comment': u = 'commentList';break;
        case 'bookmark': u = 'bookmark';break;
        case 'author':
             u = 'author';break;
    }
    if($mll || u == '' || $mlf) {
        return false;
    }
    $mll = 1;
    $.ajax({
        url: "/ajax/" + u,
        data: "page=" + $mlp + $mlm,
        dataType: "json",
        type: "POST"
    }).success(function(r) {
        var h = '';
        if(r.length > 0) {
            for(i in r) h = h + more_item(r[i]);
            if(r.length < 20) {
                $($mlts? $mlts: "#more-manga").hide();
            }
        } else {
            $mlf = true;
            $($mlts? $mlts: "#more-manga").fadeOut(500);
        }
        $($mls).append($(h));
        $mlp = $mlp + 1;
        if($mla) {
            $mla();
        }
    }).done(function(r) {
        $mll = 0;
    });
}
var more_item = function(r) {
    var h = '';
    var t = 'manga';
    switch ($mlt) {
        case 'author':
            h = '<p>Status: ' + r.status + '</p><p>' + r.rank + '</p>'
            break;
        case 'more':
            h = '<p>Status: ' + r.status + '</p><p>' + r.date + '</p>'
            break;
        case 'hot':
        case 'directory':
        case 'search':
            h = '<p>' + r.author + '</p><p>' + r.rank + '</p>'
            break;
        case 'spoiler':
        case 'news':
        case 'comment':
        case 'bookmark':
            t = $mlt;
            break;
    }
    switch (t) {
        case 'manga':
            return '<li><div class="post clearfix">'
                + '<span class="cover"><a href="' + r.manga_url + '"><img src="' + r.cover + '" /></a></span>'
                + '<div class="cover-info">'
                + '<p class="title">' + r.name + '</p>'
                + '<p>' + r.genres + '</p>'
                + h
                + '</div>'
                + '<a href="' + r.url + '" class="ch-button"><i class="reader-icon iconfont"></i>Ch' + r.no + '</a>'
                + '</div></li>';
        case 'spoiler':
            return '<li>'
                + '<p><a href="/spoiler/' + r.code + '">' + r.title + '</a></p>'
                + '<p>Post Time: ' + r.time + '</p>'
                + '</li>';
        case 'news':
            return '<li>'
                + '<p><a href="/news/' + r.code + '">' + r.title + '</a></p>'
                + '<p class="mt5"><a href="/news/' + r.code + '"><img src="' + r.cover + '" /></a></p>'
                + '</li>'
        case 'comment':
            return '<li>'
                + '<img class="comment-user" src="' + r.avatarUrl + '">'
                + '<div class="comment-content comment-hover comment-focus">'
                    + '<div class="name-time clearfix">'
                        + '<span class="name">' + r.name + '</span><span class="time">' + r.time + '</span>'
                    + '</div>'
                    + '<div class="comment-text">'
                        + comment_quote(r.quote)
                        + '<p>' + r.reply_body + '</p>'
                    + '</div>'
                    + '<div class="comment-interaction">'
                        + '<a class="comment-like" data-id="' + r.reply_id + '" data-likes="' + r.likes + '" href="javascript:void(0);" >Likes(' + r.likes + ')</a>'
                        + '<a class="comment-reply" data-id="' + r.reply_id + '" href="javascript:void(0);">Reply</a>'
                        + '<em><i></i></em>'
                    + '</div>'
                + '</div>'
                + '</li>';
        case 'bookmark':
            return '<li>'
                + '<div class="cover">'
                    + '<a href="' + r.url + '">'
                    + r.cover
                    + '<span>Read ' + r.vol + ' c' + r.no + ' ' + r.last_vol + ' c' + r.last_no + ' Updated</span>'
                    + '</a>'
                    + '<em class="bookmarkchoose iconfont" data-id="' + r.series_id + '" style="display:none;"></em>'
                + '</div>'
                + '</li>';
    }
}
function comment_quote(q) {
    if(!q) return '';
    return '<div class="comment-quote comment-hover comment-focus">'
        + '<div class="name_time clearfix">'
        + '<span class="name">' + q.name + '</span>'
        + '<span class="time">' + q.time + '</span>'
        + '</div>'
        + comment_quote(q.quote)
        + '<p>' + q.reply_body + '</p>'
        + '<div class="comment-interaction">'
        + '<a class="comment-like" data-id="' + q.reply_id + '" data-likes="' + q.likes + '" href="javascript:void(0);" >Likes(' + q.likes + ')</a>'
        + '<a class="comment-reply" data-id="' + q.reply_id + '" href="javascript:void(0);">Reply</a>'
        + '<em><i></i></em>'
        + '</div>'
        + '</div>';
}
var is_login = false;
var is_admin = false;
var series_id = 0;
var chapter_id = 0;
var series_page = 1;
$(function(){
    $('#bookmark').click(function(){
        if(!is_login) {
            noticeLogin();
            return false;
        }
        $.post('/ajax/addBookmark', {series_id:series_id, chapter_id:chapter_id, page:series_page}, function(r){
            if(r == 1) {
                noticeLogin();
            } else {
                alert(r);
            }
        })
    })
    $('#reportError').click(function(){
        var description = $('#errorDesc').val();
        $.post('/ajax/reports', {r_url:fb_url, r_description:uriEncode(description)}, function(r){
            boxRemove('manga-report');
        })
    })

    $("#emailSubscribe").click(function(){

        var email = $("#email_content").val();
        email = $.trim(email);
        var szReg=/^([a-zA-Z0-9]+[_|\_|\.]?)*[a-zA-Z0-9]+@([a-zA-Z0-9]+[_|\_|\.]?)*[a-zA-Z0-9]+\.[a-zA-Z]{2,3}$/;
        if(email==''){
            $("#email_content").val('Please enter your email!');
            return false;
        }else if(!szReg.test(email)){
            boxRemove('email-box');
            $('#tsukDisplayTips .tsuk-tip').html('Email invalid');
            boxShow('tsukDisplayTips');
            setTimeout(function(){
                boxRemove('tsukDisplayTips');
            }, 2000);
            return false;
        }else{
            $.post('/ajax/subscribe', {series_id:series_id ,email:email}, function(data){
                if(data==1 || data==2){
                    boxRemove('email-box');
                    $('#tsukDisplayTips .tsuk-tip').html('Email invalid');
                    boxShow('tsukDisplayTips');
                    setTimeout(function(){
                        boxRemove('tsukDisplayTips');
                    }, 2000);
                    return false;
                }else{
                    boxRemove('email-box');
                    $('#tsukDisplayTips .tsuk-tip').html(data);
                    boxShow('tsukDisplayTips');
                    setTimeout(function(){
                        boxRemove('tsukDisplayTips');
                    }, 2000);
                    return false;
                }

            })
        }
        return false;
    });

    $("#email_content").focus(function(){
        var email = $("#email_content").val();
        if(email == 'Please enter your email!'){
            $("#email_content").val('');
        }
        return false;
    });





});