// Token Bucket Simulation - C++
// Compile and run to see tokens and allowed/blocked decisions.
#include <bits/stdc++.h>
using namespace std;
class TokenBucket {
  double capacity;
  double tokens;
  double refill_per_sec;
  double last_time;
public:
  TokenBucket(double cap, double refill):capacity(cap),tokens(cap),refill_per_sec(refill){
    last_time = clock()/ (double)CLOCKS_PER_SEC;
  }
  bool allow(){
    double now = clock()/(double)CLOCKS_PER_SEC;
    double delta = now - last_time;
    last_time = now;
    tokens = min(capacity, tokens + delta*refill_per_sec);
    if(tokens >= 1.0){
      tokens -= 1.0;
      return true;
    }
    return false;
  }
};

int main(){
  TokenBucket tb(5, 1.0); // capacity 5, refill 1 token/sec
  for(int i=0;i<12;i++){
    bool ok = tb.allow();
    cout << i << ": " << (ok?"allowed":"blocked") << "\n";
    this_thread::sleep_for(chrono::milliseconds(300));
  }
  return 0;
}
