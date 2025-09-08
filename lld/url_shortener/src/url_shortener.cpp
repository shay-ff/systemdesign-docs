// Minimal URL shortener (in-memory) - C++
// Compile: g++ -std=c++17 url_shortener.cpp -o url_shortener && ./url_shortener
#include <bits/stdc++.h>
using namespace std;

string base62_chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
string encode_base62(long long num){
    if(num==0) return "0";
    string s;
    while(num>0){
        s.push_back(base62_chars[num%62]);
        num/=62;
    }
    reverse(s.begin(), s.end());
    return s;
}
long long decode_base62(const string &s){
    long long num=0;
    for(char c: s){
        num = num*62 + base62_chars.find(c);
    }
    return num;
}

class URLShortener {
    unordered_map<long long,string> db;
    long long counter=1;
public:
    string shorten(const string &url){
        long long id = counter++;
        db[id] = url;
        return encode_base62(id);
    }
    string expand(const string &code){
        long long id = decode_base62(code);
        if(db.find(id)==db.end()) return "";
        return db[id];
    }
};

int main(){
    URLShortener s;
    string short1 = s.shorten("https://example.com/long/path");
    cout << "short: " << short1 << endl;
    cout << "expand: " << s.expand(short1) << endl;
    return 0;
}
