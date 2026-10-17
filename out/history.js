// Compression: minified

function array(){return Array.prototype.slice.call(arguments);}
function array_key_exists(key,search){if(!search||(search.constructor!==Array&&search.constructor!==Object)){return false;}
return key in search;}
function serialize(mixed_value){var _utf8Size=function(str){var size=0,i=0,l=str.length,code='';for(i=0;i<l;i++){code=str.charCodeAt(i);if(code<0x0080){size+=1;}else if(code<0x0800){size+=2;}else{size+=3;}}
return size;};var _getType=function(inp){var type=typeof inp,match;var key;if(type==='object'&&!inp){return'null';}
if(type==="object"){if(!inp.constructor){return'object';}
var cons=inp.constructor.toString();match=cons.match(/(\w+)\(/);if(match){cons=match[1].toLowerCase();}
var types=["boolean","number","string","array"];for(key in types){if(cons==types[key]){type=types[key];break;}}}
return type;};var type=_getType(mixed_value);var val,ktype='';switch(type){case"function":val="";break;case"boolean":val="b:"+(mixed_value?"1":"0");break;case"number":val=(Math.round(mixed_value)==mixed_value?"i":"d")+":"+mixed_value;break;case"string":val="s:"+_utf8Size(mixed_value)+":\""+mixed_value+"\"";break;case"array":case"object":val="a";var count=0;var vals="";var okey;var key;for(key in mixed_value){if(mixed_value.hasOwnProperty(key)){ktype=_getType(mixed_value[key]);if(ktype==="function"){continue;}
okey=(key.match(/^[0-9]+$/)?parseInt(key,10):key);vals+=this.serialize(okey)+this.serialize(mixed_value[key]);count++;}}
val+=":"+count+":{"+vals+"}";break;case"undefined":default:val="N";break;}
if(type!=="object"&&type!=="array"){val+=";";}
return val;}
function unserialize(data){var that=this;var utf8Overhead=function(chr){var code=chr.charCodeAt(0);if(code<0x0080){return 0;}
if(code<0x0800){return 1;}
return 2;};var error=function(type,msg,filename,line){throw new that.window[type](msg,filename,line);};var read_until=function(data,offset,stopchr){var buf=[];var chr=data.slice(offset,offset+1);var i=2;while(chr!=stopchr){if((i+offset)>data.length){error('Error','Invalid');}
buf.push(chr);chr=data.slice(offset+(i-1),offset+i);i+=1;}
return[buf.length,buf.join('')];};var read_chrs=function(data,offset,length){var buf;buf=[];for(var i=0;i<length;i++){var chr=data.slice(offset+(i-1),offset+i);buf.push(chr);length-=utf8Overhead(chr);}
return[buf.length,buf.join('')];};var _unserialize=function(data,offset){var readdata;var readData;var chrs=0;var ccount;var stringlength;var keyandchrs;var keys;if(!offset){offset=0;}
var dtype=(data.slice(offset,offset+1)).toLowerCase();var dataoffset=offset+2;var typeconvert=function(x){return x;};switch(dtype){case'i':typeconvert=function(x){return parseInt(x,10);};readData=read_until(data,dataoffset,';');chrs=readData[0];readdata=readData[1];dataoffset+=chrs+1;break;case'b':typeconvert=function(x){return parseInt(x,10)!==0;};readData=read_until(data,dataoffset,';');chrs=readData[0];readdata=readData[1];dataoffset+=chrs+1;break;case'd':typeconvert=function(x){return parseFloat(x);};readData=read_until(data,dataoffset,';');chrs=readData[0];readdata=readData[1];dataoffset+=chrs+1;break;case'n':readdata=null;break;case's':ccount=read_until(data,dataoffset,':');chrs=ccount[0];stringlength=ccount[1];dataoffset+=chrs+2;readData=read_chrs(data,dataoffset+1,parseInt(stringlength,10));chrs=readData[0];readdata=readData[1];dataoffset+=chrs+2;if(chrs!=parseInt(stringlength,10)&&chrs!=readdata.length){error('SyntaxError','String length mismatch');}
readdata=that.utf8_decode(readdata);break;case'a':readdata={};keyandchrs=read_until(data,dataoffset,':');chrs=keyandchrs[0];keys=keyandchrs[1];dataoffset+=chrs+2;for(var i=0;i<parseInt(keys,10);i++){var kprops=_unserialize(data,dataoffset);var kchrs=kprops[1];var key=kprops[2];dataoffset+=kchrs;var vprops=_unserialize(data,dataoffset);var vchrs=vprops[1];var value=vprops[2];dataoffset+=vchrs;readdata[key]=value;}
dataoffset+=1;break;default:error('SyntaxError','Unknown / Unhandled data type(s): '+dtype);break;}
return[dtype,dataoffset-offset,typeconvert(readdata)];};return _unserialize((data+''),0)[2];}
function utf8_decode(str_data){var tmp_arr=[],i=0,ac=0,c1=0,c2=0,c3=0;str_data+='';while(i<str_data.length){c1=str_data.charCodeAt(i);if(c1<128){tmp_arr[ac++]=String.fromCharCode(c1);i++;}else if(c1>191&&c1<224){c2=str_data.charCodeAt(i+1);tmp_arr[ac++]=String.fromCharCode(((c1&31)<<6)|(c2&63));i+=2;}else{c2=str_data.charCodeAt(i+1);c3=str_data.charCodeAt(i+2);tmp_arr[ac++]=String.fromCharCode(((c1&15)<<12)|((c2&63)<<6)|(c3&63));i+=3;}}
return tmp_arr.join('');}
function utf8_encode(argString){var string=(argString+'');var utftext="",start,end,stringl=0;start=end=0;stringl=string.length;for(var n=0;n<stringl;n++){var c1=string.charCodeAt(n);var enc=null;if(c1<128){end++;}else if(c1>127&&c1<2048){enc=String.fromCharCode((c1>>6)|192)+String.fromCharCode((c1&63)|128);}else{enc=String.fromCharCode((c1>>12)|224)+String.fromCharCode(((c1>>6)&63)|128)+String.fromCharCode((c1&63)|128);}
if(enc!==null){if(end>start){utftext+=string.slice(start,end);}
utftext+=enc;start=end=n+1;}}
if(end>start){utftext+=string.slice(start,stringl);}
return utftext;}
function in_array (needle, haystack, argStrict) {
    var key = '',        strict = !! argStrict;

    if (strict) {
        for (key in haystack) {
            if (haystack[key] === needle) {                return true;
            }
        }
    } else {
        for (key in haystack) {            if (haystack[key] == needle) {
                return true;
            }
        }
    }
    return false;
}
function array_push (inputArr) {
    var i = 0,        pr = '',
        argv = arguments,
        argc = argv.length,
        allDigits = /^\d$/,
        size = 0,        highestIdx = 0,
        len = 0;
    if (inputArr.hasOwnProperty('length')) {
        for (i = 1; i < argc; i++) {
            inputArr[inputArr.length] = argv[i];        }
        return inputArr.length;
    }

        for (pr in inputArr) {
        if (inputArr.hasOwnProperty(pr)) {
            ++len;
            if (pr.search(allDigits) !== -1) {
                size = parseInt(pr, 10);                highestIdx = size > highestIdx ? size : highestIdx;
            }
        }
    }
    for (i = 1; i < argc; i++) {        inputArr[++highestIdx] = argv[i];
    }
    return len + i - 1;
}
var setCookie=function(c_name,value,expiredays) {
    var exdate=new Date()
    exdate.setTime(exdate.getTime()+expiredays*24*3600*1000)
        cookieVal=c_name+ "=" +escape(value)+((expiredays==null) ? "" : ";expires="+exdate.toGMTString())+";domain=.mangafox.me;path=/";
//    alert(cookieVal);
    document.cookie=cookieVal;
}
function getCookie(c_name) {
    if (document.cookie.length>0)
      {
      c_start=document.cookie.indexOf(c_name + "=")
      if (c_start!=-1)
        { 
        c_start=c_start + c_name.length+1 
        c_end=document.cookie.indexOf(";",c_start)
        if (c_end==-1) c_end=document.cookie.length
//        document.write(document.cookie.substring(c_start,c_end)+"<br>");
        return unescape(document.cookie.substring(c_start,c_end))
        } 
      }
    return ""
}
Object.size = function(obj) {
    var size = 0, key;
    for (key in obj) {
        if (obj.hasOwnProperty(key)) size++;
    }
    return size;
};
Object.shift = function(obj) {
    var key,val;
    for (key in obj) {
        if (obj.hasOwnProperty(key))
		{
			val = obj[key];
			delete obj[key];
			return val;
		}
    }
};