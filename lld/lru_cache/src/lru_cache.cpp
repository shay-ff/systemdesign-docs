// Simple LRU Cache in C++ (single-file)
// Compile: g++ -std=c++17 lru_cache.cpp -o lru && ./lru
#include <bits/stdc++.h>
using namespace std;

class LRUCache {
  struct Node {
    int key; int val;
    Node *prev, *next;
    Node(int k,int v):key(k),val(v),prev(nullptr),next(nullptr){}
  };
  int capacity;
  unordered_map<int, Node*> mp;
  Node *head, *tail;
  void remove(Node* n){
    n->prev->next = n->next;
    n->next->prev = n->prev;
  }
  void insert_front(Node* n){
    n->next = head->next;
    n->prev = head;
    head->next->prev = n;
    head->next = n;
  }
public:
  LRUCache(int cap):capacity(cap){
    head = new Node(-1,-1);
    tail = new Node(-1,-1);
    head->next = tail; tail->prev = head;
  }
  int get(int key){
    if(mp.find(key)==mp.end()) return -1;
    Node* n = mp[key];
    remove(n);
    insert_front(n);
    return n->val;
  }
  void put(int key, int value){
    if(mp.find(key)!=mp.end()){
      Node* n = mp[key];
      n->val = value;
      remove(n);
      insert_front(n);
      return;
    }
    if((int)mp.size() == capacity){
      Node* lru = tail->prev;
      remove(lru);
      mp.erase(lru->key);
      delete lru;
    }
    Node* n = new Node(key,value);
    insert_front(n);
    mp[key] = n;
  }
};

int main(){
  LRUCache cache(2);
  cache.put(1,1);
  cache.put(2,2);
  cout<<cache.get(1)<<"\n"; // 1
  cache.put(3,3); // evicts key 2
  cout<<cache.get(2)<<"\n"; // -1
  cache.put(4,4); // evicts key 1
  cout<<cache.get(1)<<"\n"; // -1
  cout<<cache.get(3)<<"\n"; // 3
  cout<<cache.get(4)<<"\n"; // 4
  return 0;
}
