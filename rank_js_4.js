/// <reference path="jquery-2.1.4.min.js" />
laytype = 2;
$('body').append('<style>.manga-list-2-cover-img{height:' + $('body').width() * 0.395 + 'px}</style>');
$(function () {
    if ("undefined" != typeof currentranktype && currentranktype > 0) {
        showrank();
        loadrank(1);
    }
    addLayoutBack();
});
totalrankload = false;
monthrankload = false;
weekrankload = false;
dayrankload = false;
function showrank() {
   
    $(".rank-select-bar a").click(
    function () {
        $(".rank-select-bar a").removeClass("active");
        $(".ranking").hide();

        $(this).addClass("active");
        var data = $(this).attr("data");
        $("." + data).html("<div class='loading' style='padding-top:10px;font-size:12px;color:#767676;text-align: center;'>loading...</div>");
        if (data == "totalrank" && $("." + data + " .line-list").length == 0) {
            $("." + data).load("/totalranking/" + " ." + data + " .line-list");
            totalrankload = true;
        }
        else if (data == "monthrank" && $("." + data + " .line-list").length == 0) {
            $("." + data).load("/monthranking/" + " ." + data + " .line-list");
            monthrankload = true;
        }
        else if (data == "weekrank" && $("." + data + " .line-list").length == 0) {
            $("." + data).load("/weekranking/" + " ." + data + " .line-list");
            weekrankload = true;
        }
        else if (data == "dayrank" && $("." + data + " .line-list").length == 0) {
            $("." + data).load("/dayranking/" + " ." + data + " .line-list");
            dayrankload = true;
        }
        $("." + data).show();
       
    });
}

function loadrank(i) {
    if (i > 0 && i <= 4) {
        if (typeof (currentranktype) != "undefined" && i == currentranktype) {
            loadrank(i + 1);
            return;
        }
        var data = "";
        if (i == 1) {
            data = "totalrank";
            if ($("." + data + " .line-list").length == 0) {
                $("." + data).load("/totalranking/" + " ." + data + " .line-list", function () { loadrank(i + 1); });
                totalrankload = true;
            }
            else {
                loadrank(i + 1);
            }
        } else if (i == 2) {
            data = "monthrank";
            if ($("." + data + " .line-list").length == 0) {
                $("." + data).load("/monthranking/" + " ." + data + " .line-list", function () { loadrank(i + 1); });
                monthrankload = true;
            }
            else {
                loadrank(i + 1);
            }
        }
        else if (i == 3) {
            data = "weekrank";
            if ($("." + data + " .line-list").length == 0) {
                $("." + data).load("/weekranking/" + " ." + data + " .line-list", function () { loadrank(i + 1); });
                weekrankload = true;
            }
            else {
                loadrank(i + 1);
            }
        }
        else if (i == 4) {
            data = "dayrank";
            if ($("." + data + " .line-list").length == 0) {
                $("." + data).load("/dayranking/" + " ." + data + " .line-list", function () { loadrank(i + 1); });
                dayrankload = true;
            }
        }
        
    }
}