// "Extract side effects as an 'if' statement" "true-preview"
class Z {

  void z() {
    int i = 0;
    for(int j=0;j<100;j++) {
        if ((i < 100)) {
            i++;
        } else {
            i--;
        }
    }
  }
}